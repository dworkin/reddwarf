package com.sun.gi.utils.nio;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import static java.nio.channels.SelectionKey.*;
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
public class NIOConnection implements SelectorHandler {

    private static Logger log = Logger.getLogger("com.sun.gi.utils.nio");

    protected final NIOSocketManager socketManager;
    protected final PacketHandler    tcpHandler;
    protected final PacketHandler    udpHandler;

    // made available for filling by the manager
    protected ByteBuffer inputBuffer;

    protected ByteBuffer sizeHeader ;
    protected int currentPacketSize = -1;
    protected ByteBuffer outputHeader = ByteBuffer.allocate(4);

    Set<NIOConnectionListener> listeners =
	new TreeSet<NIOConnectionListener>();

    public NIOConnection(NIOSocketManager mgr,
			 SocketChannel    sockChannel,
			 DatagramChannel  dgramChannel,
			 int tcpBufSize,
			 int udpBufSize) {

	socketManager = mgr;

	tcpHandler = new PacketHandler(this, sockChannel, tcpBufSize);
	udpHandler = new PacketHandler(this, dgramChannel, udpBufSize);
    }

    public void open(Selector sel) throws IOException {
	tcpHandler.open(sel);
	udpHandler.open(sel);
    }

    public void registerConnect(Selector sel) throws IOException {
	tcpHandler.channel.register(sel, OP_CONNECT, this);
    }

    public void processConnect(SelectionKey key) throws IOException {
	SocketChannel sc = (SocketChannel) key.channel();
	if (sc.finishConnect()) {
	    tcpHandler.open(key.selector());
	    Socket sock = sc.socket();

	    // Point the UDP channel to the right endpoint
	    DatagramChannel dc = (DatagramChannel) udpHandler.channel;
	    DatagramSocket ds = dc.socket();
	    ds.bind(sock.getLocalSocketAddress());
	    dc.connect(sock.getRemoteSocketAddress());
	    udpHandler.open(key.selector());
	}
    }
    
    void packetReceived(ByteBuffer pkt) {
	for (NIOConnectionListener l : listeners) {
	    l.packetReceived(this, pkt);
	}
    }

    class PacketHandler
	    implements ReadWriteSelectorHandler {

	final NIOConnection parent;
	final SelectableChannel channel;
	final ByteBuffer sendBuffer;
	final ByteBuffer recvBuffer;

	protected int nextRecvPacketLen = 0;
	protected SelectionKey key = null;

	public PacketHandler(NIOConnection conn,
		SelectableChannel chan, int bufSize) {
	    parent = conn;
	    channel = chan;
	    sendBuffer = ByteBuffer.allocateDirect(bufSize);
	    recvBuffer = ByteBuffer.allocateDirect(bufSize);
	}

	public void open(Selector sel) {
	    try {
		key = channel.register(sel, OP_READ, this);
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	protected boolean processRecvBuffer() throws IOException {
	    recvBuffer.flip();
	    boolean buffer_empty = false;

	    for (;;) {
		if (nextRecvPacketLen == 0) {
		    // We're waiting for a frame header (an int)

		    if (recvBuffer.remaining() == 0) {
			// No partial packets remain in the buffer
			buffer_empty = true;
			break;
		    }

		    if (recvBuffer.remaining() < 4) {
			log.fine("Waiting for a packet header -- "
				+ "only have " + recvBuffer.remaining());
			break;
		    }

		    // Got frame header
		    int packet_len = recvBuffer.getInt();
		    if (packet_len <= 0) {
			log.warning("Bad packet length: " + packet_len);
			break;
		    }

		    // Now we know the new packet's length
		    nextRecvPacketLen = packet_len;
		}

		if (recvBuffer.remaining() < nextRecvPacketLen) {
		    // We don't have all of the packet
		    break;
		}

		// Got a whole packet; dispatch it
		ByteBuffer packet = recvBuffer.slice();
		packet.limit(nextRecvPacketLen);
		recvBuffer.position(recvBuffer.position() + nextRecvPacketLen);
		nextRecvPacketLen = 0;
		parent.packetReceived(packet);

		// Loop around and see if we can dispatch some more
	    }

	    recvBuffer.compact();
	    return buffer_empty;
	}

	public void handleRead(SelectionKey key) throws IOException {
	    if (! channel.isOpen()) {
		throw new IOException("not open");
	    }

	    log.finest("channel is a " + channel.getClass());

	    int rc = ((ReadableByteChannel) channel).read(recvBuffer);

	    if (rc <= 0) {
		throw new IOException("Error reading");
	    }

	    processRecvBuffer();
	}

	public void handleWrite(SelectionKey key) throws IOException {
	    int wc = 0;
	    boolean bufferEmpty;

	    synchronized (sendBuffer) {
		sendBuffer.flip();
		wc = ((WritableByteChannel) channel).write(sendBuffer);
		bufferEmpty = (! sendBuffer.hasRemaining());
		sendBuffer.compact();
	    }

	    if (bufferEmpty) {
		key.interestOps(key.interestOps()
				& (~ SelectionKey.OP_WRITE));
	    }
	}

	public void handleClose() {
	    parent.handleClose();
	}

	public void close() {
	    try {
		log.fine("Closing " + channel);
		channel.close();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	public void send(ByteBuffer[] packetParts) {
	    int sz = 0;
	    for (ByteBuffer buf : packetParts) {
		buf.flip();
		sz += buf.remaining();
	    }
	    synchronized(sendBuffer){
		sendBuffer.putInt(sz);
		for (ByteBuffer buf : packetParts) {
		    sendBuffer.put(buf);
		}
	    }
	    parent.socketManager.enableWrite(channel);
	}

    }

    public void handleClose() {
	tcpHandler.close();
	udpHandler.close();

	for (NIOConnectionListener l : listeners) {
	    l.disconnected(this);
	}
    }

    public void disconnect() {
	handleClose();
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

	PacketHandler h = reliable ? tcpHandler : udpHandler;
	h.send(packetParts);
    }
}
