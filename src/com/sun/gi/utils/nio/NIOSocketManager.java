package com.sun.gi.utils.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.Socket;
import java.net.DatagramSocket;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

import java.lang.reflect.Method;
import java.nio.channels.spi.SelectorProvider;
import sun.nio.ch.DefaultSelectorProvider;

// @todo Non-blocking writes

public class NIOSocketManager implements Runnable {

    private static Logger log = Logger.getLogger("com.sun.gi.utils.nio");

    private Selector selector;
    private Set<NIOSocketManagerListener> listeners =
	new TreeSet<NIOSocketManagerListener>();
    private int initialInputBuffSz;
    private List<NIOConnection> initiatorQueue =
	new ArrayList<NIOConnection>();
    private List<NIOConnection> receiverQueue =
	new ArrayList<NIOConnection>();
    private List<ServerSocketChannel> acceptorQueue =
	new ArrayList<ServerSocketChannel>();
    private static SelectorProvider selectorProvider;

    static {
	//DefaultSelectorProvider foo;
	try {
	    if (System.getProperty("sgs.nio.forceprovider") != null) {
		Class selectorProviderClass =
		    Class.forName("sun.nio.ch.DefaultSelectorProvider");
		if (selectorProviderClass == null) {
		    log.warning("Cannot find default provider; cannot force.");
		    System.exit(8001); // XXX Don't exit
		}
		Method factory =
		    selectorProviderClass.getMethod("create", new Class[] {});
		selectorProvider = (SelectorProvider) factory.invoke(null);
	    } else {
		selectorProvider = SelectorProvider.provider();
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(8001);         // XXX Don't exit
	}
	log.info("selectorProvider = " + selectorProvider);
    }

    public NIOSocketManager() throws IOException {
	this(64 * 1024);
    }

    public NIOSocketManager(int inputBufferSize) throws IOException {
	selector = selectorProvider.openSelector();
	initialInputBuffSz = inputBufferSize;
	new Thread(this).start();
    }

    public void acceptConnectionsOn(SocketAddress addr)
	    throws IOException {
	log.entering("NIOSocketManager", "acceptConnectionsOn");

	ServerSocketChannel channel =
	    selectorProvider.openServerSocketChannel();
	channel.configureBlocking(false);
	channel.socket().bind(addr);

	synchronized (acceptorQueue) {
	    acceptorQueue.add(channel);
	}
	selector.wakeup();

	log.exiting("NIOSocketManager", "acceptConnectionsOn");
    }

    public NIOConnection makeConnectionTo(SocketAddress addr) {
	log.entering("NIOSocketManager", "makeConnectionTo");

	try {
	    SocketChannel sc = selectorProvider.openSocketChannel();
	    sc.configureBlocking(false);
	    sc.connect(addr);

	    DatagramChannel dchan = selectorProvider.openDatagramChannel();
	    dchan.configureBlocking(false);

	    NIOConnection conn =
		new NIOConnection(sc, dchan, initialInputBuffSz);

	    synchronized (initiatorQueue) {
		initiatorQueue.add(conn);
	    }
	    selector.wakeup();

	    return conn;
	}
	catch (IOException ex) {
	    ex.printStackTrace();
	    return null;
	} finally {
	    log.exiting("NIOSocketManager", "makeConnectionTo");
	}
    }

    // This runs the actual polled input

    public void run() {
	log.entering("NIOSocketManager", "run");

	while (true) {    // until shutdown() is called

	    synchronized (initiatorQueue) {
		for (NIOConnection conn : initiatorQueue) {
		    try {
			conn.open(selector, SelectionKey.OP_CONNECT);
		    }
		    catch (IOException ex) {
			ex.printStackTrace();
		    }
		}
		initiatorQueue.clear();
	    }

	    synchronized (receiverQueue) {
		for (NIOConnection conn : receiverQueue) {
		    try {
			conn.open(selector, SelectionKey.OP_READ);
		    }
		    catch (IOException ex) {
			ex.printStackTrace();
		    }
		}
		receiverQueue.clear();
	    }

	    synchronized (acceptorQueue) {
		for (ServerSocketChannel chan : acceptorQueue) {
		    try {
			chan.register(selector, SelectionKey.OP_ACCEPT);
		    }
		    catch (ClosedChannelException ex2) {
			ex2.printStackTrace();
		    }
		}
		acceptorQueue.clear();
	    }

	    if (! selector.isOpen())
		break;

	    try {
		log.finest("Calling select");

		int n = selector.select();

		log.finer("selector: " + n + " ready handles");

		if (n > 0) {
		    processSocketEvents(selector);
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}
	log.exiting("NIOSocketManager", "run");
    }

    /**
     * processSocketEvents
     *
     * @param selector Selector
     */
    private void processSocketEvents(Selector selector) {
	log.entering("NIOSocketManager", "processSocketEvents");

	Iterator<SelectionKey> i = selector.selectedKeys().iterator();

	// Walk through set
	while (i.hasNext()) {

	    // Get key from set
	    SelectionKey key = (SelectionKey) i.next();

	    // Remove current entry
	    i.remove();

	    // for accepting connections
	    if (key.isValid() && key.isAcceptable()) {
		processAccept(selector, key);
	    }

	    //for reading from connections.
	    if (key.isValid() && key.isReadable()) {
		processInput(key);
	    }

	    // to finish the connecting process
	    if (key.isValid() && key.isConnectable()) {
		processConnect(key);
	    }
	}

	log.exiting("NIOSocketManager", "processSocketEvents");
    }

    /**
     * processInput
     *
     * @param key SelectionKey
     */
    private void processInput(SelectionKey key) {
	log.entering("NIOSocketManager", "processInput");
	NIOConnection conn = (NIOConnection) key.attachment();
	try {
	    conn.dataArrived((ReadableByteChannel) key.channel());
	} catch (IOException ex) {
	    conn.disconnect();
	} finally {
	    log.exiting("NIOSocketManager", "processInput");
	}
    }

    /**
     * processAccept
     *
     * @param selector Selector
     * @param key SelectionKey
     */
    private void processAccept(Selector selector, SelectionKey key) {
	log.entering("NIOSocketManager", "processAccept");
	// Get channel
	ServerSocketChannel serverChannel =
	    (ServerSocketChannel) key.channel();

	NIOConnection conn = null;

	// Accept request
	try {
	    SocketChannel sc = serverChannel.accept();
	    if (sc == null) {
		log.warning("accept returned null");
		return;
	    }
	    sc.configureBlocking(false);

	    // Now create a UDP channel for this endpoint

	    DatagramChannel dchan = selectorProvider.openDatagramChannel();
	    conn = new NIOConnection(sc, dchan, initialInputBuffSz);

	    dchan.socket().setReuseAddress(true);
	    dchan.configureBlocking(false);
	    dchan.socket().bind(sc.socket().getLocalSocketAddress());
	    dchan.connect(sc.socket().getRemoteSocketAddress());

	    log.finest("udp local " +
		dchan.socket().getLocalSocketAddress() +
		" remote " + dchan.socket().getRemoteSocketAddress());

	    synchronized (receiverQueue) {
		receiverQueue.add(conn);
	    }

	    for (NIOSocketManagerListener l : listeners) {
		l.newConnection(conn);
	    }
	} catch (IOException ex) {
	    ex.printStackTrace();
	    if (conn != null) {
		conn.disconnect();
	    }
	} finally {
	    log.exiting("NIOSocketManager", "processAccept");
	}
    }

    /**
     * processConnect
     *
     * @param key SelectionKey
     */
    private void processConnect(SelectionKey key) {
	log.entering("NIOSocketManager", "processConnect");

	NIOConnection conn = (NIOConnection) key.attachment();
	try {
	    conn.processConnect(key);
	    for (NIOSocketManagerListener l : listeners) {
		l.connected(conn);
	    }
	} catch (IOException ex) {
	    log.warning("NIO connect failure: "+ex.getMessage());
	    conn.disconnect();
	    for (NIOSocketManagerListener l : listeners) {
		l.connectionFailed(conn);
	    }
	} finally {
	    log.exiting("NIOSocketManager", "processConnect");
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

    public void shutdown() {
	try {
	    selector.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

}
