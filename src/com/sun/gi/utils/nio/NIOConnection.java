package com.sun.gi.utils.nio;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 * @todo Set SO_RCVBUF and SO_SNDBUF on the channels
 */
public class NIOConnection {

    private static Logger log = Logger.getLogger("com.sun.gi.utils.nio");

    protected final SocketChannel   reliableChannel;
    protected final DatagramChannel unreliableChannel;

    // made available for filling by the manager
    protected ByteBuffer inputBuffer;

    protected ByteBuffer sizeHeader ;
    protected int currentPacketSize = -1;
    protected ByteBuffer outputHeader = ByteBuffer.allocate(4);
    protected Set<NIOConnectionListener> listeners =
	new TreeSet<NIOConnectionListener>();

    // package private factory

    public NIOConnection(SocketChannel reliableChan,
			 DatagramChannel unreliableChan,
			 int initialMaxPacketSize) {
	reliableChannel = reliableChan;
	unreliableChannel = unreliableChan;
	inputBuffer = ByteBuffer.allocate(initialMaxPacketSize);
	sizeHeader = ByteBuffer.allocate(4);
    }

    public void open(Selector selector, int ops) throws IOException {
	log.entering("NIOConnection", "open");
	reliableChannel.register(selector, ops, this);
	if ((ops & SelectionKey.OP_READ) != 0) {
	    unreliableChannel.register(selector, SelectionKey.OP_READ, this);
	}
	log.exiting("NIOConnection", "open");
    }

    public void processConnect(SelectionKey key) throws IOException {
	if (reliableChannel.finishConnect()) {
	    key.interestOps(SelectionKey.OP_READ);

	    // Point the UDP channel to the right endpoint
	    unreliableChannel.socket().bind(
		reliableChannel.socket().getLocalSocketAddress());
	    unreliableChannel.connect(
		reliableChannel.socket().getRemoteSocketAddress());
	    unreliableChannel.register(key.selector(),
		SelectionKey.OP_READ, this);

	    log.finest("udp local " +
		unreliableChannel.socket().getLocalSocketAddress() +
		" remote " +
		unreliableChannel.socket().getRemoteSocketAddress());
	}
    }

    /**
     * dataArrived
     */
    public void dataArrived(ReadableByteChannel chan) throws IOException {
	log.entering("NIOConnection", "dataArrived");
	int bytesRead=-1;
	if (!chan.isOpen()){
	    IOException e = new IOException("not open");
	    log.throwing("NIOConnection", "dataArrived", e);
	    throw e;
	}
	log.finest("chan is a " + chan.getClass());
	do {
	    if (currentPacketSize == -1) {// getting size
		bytesRead = chan.read(sizeHeader);
		if (!sizeHeader.hasRemaining()){ // have header
		    sizeHeader.flip();
		    currentPacketSize = sizeHeader.getInt();
		    if (inputBuffer.capacity() < currentPacketSize){
			inputBuffer = ByteBuffer.allocate(currentPacketSize);
		    } else {
			inputBuffer.limit(currentPacketSize);
		    }
		}
	    } else {
		bytesRead = chan.read(inputBuffer);
		if (!inputBuffer.hasRemaining()) { // have packet
		    // change from "writeten" state to "read" state
		    inputBuffer.flip();
		    for (NIOConnectionListener l : listeners) {
			l.packetReceived(this, inputBuffer.asReadOnlyBuffer());
		    }
		    inputBuffer.clear();
		    sizeHeader.clear();
		    currentPacketSize = -1;
		}
	    }
	} while (bytesRead>0);
	if (bytesRead == -1) { // closed
	    disconnect();
	}
	log.exiting("NIOConnection", "dataArrived");
    }

    /**
     * disconnect
     */
    public void disconnect() {
	try {
	    close();
	} catch (IOException ex) {
	    ex.printStackTrace();
	}
	for (NIOConnectionListener l : listeners) {
	    l.disconnected(this);
	}
    }

    /**
     * addListener
     *
     * @param l NIOConnectionListener
     */
    public void addListener(NIOConnectionListener l) {
	listeners.add(l);
    }

    /**
     * removeListener
     *
     * @param l NIOConnectionListener
     */
    public void removeListener(NIOConnectionListener l) {
	listeners.remove(l);
    }

    /**
     * send
     *
     * @param packet ByteBuffer
     */
    public synchronized void send(ByteBuffer packet) throws IOException {
	send(packet, true);
    }

    /**
     * send
     *
     * @param packet ByteBuffer
     * @param reliable boolean
     */
    public synchronized void send(ByteBuffer packet, boolean reliable)
	    throws IOException {
	send(new ByteBuffer[] { packet }, reliable);
    }

    /**
     * send
     *
     * @param packetParts ByteBuffer[]
     */
    public synchronized void send(ByteBuffer[] packetParts)
	    throws IOException {
	send(packetParts, true);
    }

    /**
     * send
     *
     * @param packetParts ByteBuffer[]
     * @param reliable boolean
     */
    public synchronized void send(ByteBuffer[] packetParts, boolean reliable)
	    throws IOException {
	log.entering("NIOChannel", "send[]");

	GatheringByteChannel chan =
	    reliable ? reliableChannel : unreliableChannel;

	int sz = 0;
	for (ByteBuffer buf : packetParts) {
	    buf.flip();
	    sz += buf.remaining();
	}
	synchronized(outputHeader){
	    outputHeader.clear();
	    outputHeader.putInt(sz);
	    outputHeader.flip();
	    chan.write(outputHeader);
	}
	chan.write(packetParts);
	log.exiting("NIOChannel", "send[]");
    }

    public void close() throws IOException {
	IOException ex = null;
	try {
	    reliableChannel.close();
	} catch (IOException e) {
	    ex = e;
	}

	unreliableChannel.close();

	if (ex != null) {
	    throw ex;
	}
    }
}
