package com.sun.gi.utils.nio;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import java.nio.channels.spi.SelectorProvider;

import java.lang.reflect.Method;
import sun.nio.ch.DefaultSelectorProvider;

public class NIOSocketManager
    implements Runnable {
  private Selector selector;
  private boolean done = false;
  private List listeners = new ArrayList();
  private Map chanToConnObject = new HashMap();
  private int initialInputBuffSz;
  private Object selectorEmptyMutex = new Object();
  private List initiatorQueue = new ArrayList();
  private List receiverQueue = new ArrayList();
  private List accepterQueue = new ArrayList();
  private static SelectorProvider selectorProvider;

  static {
    //DefaultSelectorProvider foo;
      try {
        if (System.getProperty("sgs.nio.forceprovider")!=null){
          Class selectorProviderClass = Class.forName("sun.nio.ch.DefaultSelectorProvider");
          if (selectorProviderClass == null) {
            System.out.println("ERROR: Cannot find default provider.  Cannot force.");
            System.exit(8001);
          }
          Method factory = selectorProviderClass.getMethod("create",new Class[]{});
          selectorProvider = (SelectorProvider) factory.invoke(null,null);
        } else {
          selectorProvider = SelectorProvider.provider();
        }

      } catch (Exception e) {
        e.printStackTrace();
        System.exit(8001);
      }
      System.out.println("selectorProvider = "+selectorProvider);
  }

  public NIOSocketManager() throws IOException {
    this(2048);
  }

  public NIOSocketManager(int inputBufferSize) throws IOException {
    selector = selectorProvider.openSelector();
    initialInputBuffSz = 2048;
    new Thread(this).start();
  }

  public void acceptTCPConnectionsOn(String host, int port) throws IOException {
    ServerSocketChannel channel = selectorProvider.openServerSocketChannel();
    channel.configureBlocking(false);
    InetSocketAddress isa = new InetSocketAddress(host, port);
    channel.socket().bind(isa);
    synchronized (accepterQueue) {
      accepterQueue.add(channel);

    }
   synchronized(selectorEmptyMutex) {
      selectorEmptyMutex.notify();
      selector.wakeup();
    }
  }

  public NIOTCPConnection makeTCPConnectionTo(String host, int port) {
    InetSocketAddress isa = new InetSocketAddress(host, port);
    SocketChannel sc = null;
    try {
      sc = selectorProvider.openSocketChannel();
      sc.configureBlocking(false);
      NIOTCPConnection conn =
          NIOTCPConnection.newConnectionObject(sc, initialInputBuffSz);
      chanToConnObject.put(sc, conn);
      synchronized (initiatorQueue) {
        initiatorQueue.add(sc);
      }
     synchronized(selectorEmptyMutex) {
        selectorEmptyMutex.notify();
        selector.wakeup();
      }

      sc.connect(isa);
      return conn;
    }
    catch (IOException ex) {
      ex.printStackTrace();
      return null;
    }
  }

  // This runs the actual polled input

  public void run() {
    while (!done) {

      synchronized (initiatorQueue) {
        for (Iterator i = initiatorQueue.iterator(); i.hasNext(); ) {
          SocketChannel chan = (SocketChannel) i.next();
          try {
            chan.register(selector,
                          SelectionKey.OP_READ |
                          SelectionKey.OP_CONNECT);
          }
          catch (ClosedChannelException ex) {
            ex.printStackTrace();
          }
        }
        initiatorQueue.clear();
      }

      synchronized (receiverQueue) {
        for (Iterator i = receiverQueue.iterator(); i.hasNext(); ) {
          SocketChannel chan = (SocketChannel) i.next();
          try {
            chan.register(selector,
                          SelectionKey.OP_READ);
          }
          catch (ClosedChannelException ex) {
            ex.printStackTrace();
          }
        }
        receiverQueue.clear();
      }


      synchronized (accepterQueue) {
        for (Iterator i = accepterQueue.iterator(); i.hasNext(); ) {
          ServerSocketChannel channel = (ServerSocketChannel) i.next();
          try {
            channel.register(selector, SelectionKey.OP_ACCEPT);
          }
          catch (ClosedChannelException ex2) {
            ex2.printStackTrace();
          }
        }
        accepterQueue.clear();
      }

      while(selector.keys().isEmpty() &&
            initiatorQueue.isEmpty() &&
            accepterQueue.isEmpty()) { // nothing to do so just wait
        synchronized(selectorEmptyMutex){
          try {
            selectorEmptyMutex.wait();
          }
          catch (InterruptedException ex3) {
            ex3.printStackTrace();
          }
        }
      }

      if (!selector.keys().isEmpty()){ // we have some potential work
        try {
          // this is tricky to understand, its making use of operator
          //short circuting.
          if (selector.select() > 0)
          {
            processSocketEvents(selector);
          }
        }
        catch (IOException ex1) {
          ex1.printStackTrace();
        }
      }
    }
  }

  /**
   * processSocketEvents
   *
   * @param selector Selector
   */
  private void processSocketEvents(Selector selector) {
    Set readyKeys = selector.selectedKeys();
    Iterator readyItor = readyKeys.iterator();
    // Walk through set
    while (readyItor.hasNext()) {
      // Get key from set
      SelectionKey key =
          (SelectionKey) readyItor.next();
      // Remove current entry
      readyItor.remove();

      // for accepting connections
      if (key.isValid() && key.isAcceptable()) {
        processNewConnection(selector, key);
      }
      //for reading from connections.
      if (key.isValid() && key.isReadable()) {
        processInput(key);
      }
      // to finish the connecting process
      if (key.isValid() && key.isConnectable()) {
        processConnectable(key);
      }


    }

  }

  /**
   * processInput
   *
   * @param key SelectionKey
   */
  private void processInput(SelectionKey key) {
    SocketChannel chan = (SocketChannel) key.channel();
    NIOTCPConnection conn =
        (NIOTCPConnection) chanToConnObject.get(chan);
    try {
      conn.dataArrived();
    }
    catch (IOException ex) {
      try {
        chan.close();
      }
      catch (IOException ex1) {
        ex1.printStackTrace();
      }
      key.cancel();
      chanToConnObject.remove(chan);
      conn.disconnected();
    }
  }

  /**
   * processNewConnection
   *
   * @param selector Selector
   * @param key SelectionKey
   */
  private void processNewConnection(Selector selector, SelectionKey key) {
    // Get channel
    ServerSocketChannel keyChannel =
        (ServerSocketChannel) key.channel();

    // Get server socket
    ServerSocket serverSocket = keyChannel.socket();

    // Accept request
    Socket socket = null;
    SelectionKey newkey = null;
    try {
      socket = serverSocket.accept();
      SocketChannel chan = socket.getChannel();
      chan.configureBlocking(false);
      NIOTCPConnection conn = NIOTCPConnection.newConnectionObject(chan,
          initialInputBuffSz);
      chanToConnObject.put(chan, conn);
      synchronized (receiverQueue) {
        receiverQueue.add(chan);
      }
     synchronized(selectorEmptyMutex) {
        selectorEmptyMutex.notify();
        //selector.wakeup();
      }
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        try {
          ( (NIOSocketManagerListener) i.next()).newTCPConnection(conn);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      chan.finishConnect();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }

  }

  /**
   * processConnectable
   *
   * @param key SelectionKey
   */
  private void processConnectable(SelectionKey key) {
    SocketChannel chan = (SocketChannel) key.channel();
    NIOTCPConnection conn =
        (NIOTCPConnection) chanToConnObject.get(chan);
    try {
      int trycount = 0;      
      while(true){ // break to exit    
        if ((trycount%10 )==0){
          System.out.println("awaiting conenction completion.");
        }
        if (chan.isConnectionPending()){
        	if (chan.finishConnect()){
        		break;
        	}
      	}
        trycount++;
        try {
          Thread.sleep(100);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      key.interestOps(SelectionKey.OP_READ);
      for(Iterator i=listeners.iterator();i.hasNext();){
        ((NIOSocketManagerListener)i.next()).connected(conn);
      }
    }
    catch (IOException ex) {
      key.cancel();
      try {
        chan.close();
      }
      catch (IOException ex1) {
        ex1.printStackTrace();
      }
      System.out.println("NIO connect failure: "+ex.getMessage());
      for(Iterator i=listeners.iterator();i.hasNext();){
        ((NIOSocketManagerListener)i.next()).connectionFailed(conn);
      }

    }
  }

  /**
   * addListener
   *
   * @param l NIOSocketManagerListener
   */
  public void addListener(NIOSocketManagerListener l) {
    listeners.add(l);
  }

}
