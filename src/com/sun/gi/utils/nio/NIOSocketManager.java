package com.sun.gi.utils.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.Socket;
import java.net.DatagramSocket;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

import static java.nio.channels.SelectionKey.*;

public class NIOSocketManager implements Runnable {

    private static Logger log = Logger.getLogger("com.sun.gi.utils.nio");

    private Selector selector;

    private int initialInputBuffSz;

    private Set<NIOSocketManagerListener> listeners =
	new TreeSet<NIOSocketManagerListener>();

    private List<NIOConnection> initiatorQueue =
	new ArrayList<NIOConnection>();

    private List<NIOConnection> receiverQueue =
	new ArrayList<NIOConnection>();

    private List<ServerSocketChannel> acceptorQueue =
	new ArrayList<ServerSocketChannel>();

    private List<SelectableChannel> writeQueue =
	new ArrayList<SelectableChannel>();

    public NIOSocketManager() throws IOException {
	this(64 * 1024);
    }

    public NIOSocketManager(int inputBufferSize) throws IOException {
	selector = Selector.open();
	initialInputBuffSz = inputBufferSize;
	new Thread(this).start();
    }

    public void acceptConnectionsOn(SocketAddress addr)
	    throws IOException {
	log.entering("NIOSocketManager", "acceptConnectionsOn");

	ServerSocketChannel channel = ServerSocketChannel.open();
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
	    SocketChannel sc = SocketChannel.open();
	    sc.configureBlocking(false);
	    sc.connect(addr);

	    DatagramChannel dc = DatagramChannel.open();
	    dc.configureBlocking(false);

	    NIOConnection conn =
		new NIOConnection(this, sc, dc, initialInputBuffSz);

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
			conn.registerConnect(selector);
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
			conn.open(selector);
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
			chan.register(selector, OP_ACCEPT);
		    }
		    catch (ClosedChannelException ex2) {
			ex2.printStackTrace();
		    }
		}
		acceptorQueue.clear();
	    }

	    synchronized (writeQueue) {
		for (SelectableChannel chan : writeQueue) {
		    SelectionKey key = chan.keyFor(selector);
		    key.interestOps(key.interestOps() | OP_WRITE);
		}
		writeQueue.clear();
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

	    if (key.isValid() && key.isAcceptable()) {
		handleAccept(key);
	    }

	    if (key.isValid() && key.isConnectable()) {
		handleConnect(key);
	    }

	    if (key.isValid() && key.isReadable()) {
		handleRead(key);
	    }

	    if (key.isValid() && key.isWritable()) {
		handleWrite(key);
	    }
	}

	log.exiting("NIOSocketManager", "processSocketEvents");
    }

    private void handleRead(SelectionKey key) {
	log.entering("NIOSocketManager", "handleRead");
	ReadWriteSelectorHandler h =
	    (ReadWriteSelectorHandler) key.attachment();
	try {
	    h.handleRead(key);
	} catch (IOException ex) {
	    h.handleClose();
	} finally {
	    log.exiting("NIOSocketManager", "handleRead");
	}
    }

    private void handleWrite(SelectionKey key) {
	log.entering("NIOSocketManager", "handleWrite");
	ReadWriteSelectorHandler h =
	    (ReadWriteSelectorHandler) key.attachment();
	try {
	    h.handleWrite(key);
	} catch (IOException ex) {
	    h.handleClose();
	} finally {
	    log.exiting("NIOSocketManager", "handleWrite");
	}
    }

    private void handleAccept(SelectionKey key) {
	log.entering("NIOSocketManager", "handleAccept");
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

	    DatagramChannel dc = DatagramChannel.open();
	    conn = new NIOConnection(this, sc, dc, initialInputBuffSz);

	    dc.socket().setReuseAddress(true);
            dc.configureBlocking(false);
	    dc.socket().bind(sc.socket().getLocalSocketAddress());
            
            // @@: Workaround for Windows JDK 1.5; it's unhappy with this
            // call because it's trying to use the (null) hostname instead
            // of the host address.  So we explicitly pull out the host
            // address and create a new InetSocketAddress with it.
	    //dc.connect(sc.socket().getRemoteSocketAddress());
            dc.connect(new InetSocketAddress(
                    sc.socket().getInetAddress().getHostAddress(),
                    sc.socket().getPort()));

	    log.finest("udp local " +
		dc.socket().getLocalSocketAddress() +
		" remote " + dc.socket().getRemoteSocketAddress());

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
	    log.exiting("NIOSocketManager", "handleAccept");
	}
    }

    public void enableWrite(SelectableChannel chan) {
	writeQueue.add(chan);
	selector.wakeup();
    }

    private void handleConnect(SelectionKey key) {
	log.entering("NIOSocketManager", "handleConnect");

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
	    log.exiting("NIOSocketManager", "handleConnect");
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
