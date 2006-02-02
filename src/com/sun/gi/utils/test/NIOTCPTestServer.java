package com.sun.gi.utils.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;

import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOSocketManagerListener;
import com.sun.gi.utils.nio.NIOConnection;
import com.sun.gi.utils.nio.NIOConnectionListener;

public class NIOTCPTestServer
	implements NIOSocketManagerListener, NIOConnectionListener {

    NIOSocketManager socketMgr;

    private volatile boolean startingUp = true;

    private List<NIOConnection> connections = new ArrayList<NIOConnection>();

    public NIOTCPTestServer() {
	try {
	    socketMgr = new NIOSocketManager();
	    socketMgr.addListener(this);
	    socketMgr.acceptConnectionsOn(
		new InetSocketAddress("localhost", 1138));
	}
	catch (IOException ex) {
	    ex.printStackTrace();
	}
    }

    static public void main(String[] args) {
	new NIOTCPTestServer();
    }

    // NIOSocketManagerListener methods

    public void newConnection(NIOConnection connection) {
	System.err.println("Someone connected!");
	startingUp = false;
	connection.addListener(this);
	connections.add(connection);
    }

    public void connected(NIOConnection conn) {
	System.err.println("Weird, got connected callback");
    }

    public void connectionFailed(NIOConnection conn) {
	System.err.println("Weird, got connection failed callback");
    }

    // NIOChannelListener methods

    public void packetReceived(NIOConnection conn, ByteBuffer inputBuffer) {
	byte[] inbytes = new byte[inputBuffer.remaining()];
	inputBuffer.get(inbytes);
	String msg = new String(inbytes);
	System.out.println("Received: " + msg);
	boolean reliable = ! msg.startsWith("!");
	for (NIOConnection tconn : connections) {
	    if (tconn != conn) { // dont send back to sender
		try {
		    tconn.send(inputBuffer, reliable);
		} catch (IOException ex) {
		    ex.printStackTrace();
		}
	    }
	}
    }

    public void disconnected(NIOConnection conn) {
	System.err.println("Socket disconnected!");
	connections.remove(conn);
	if (connections.isEmpty()) {
	    socketMgr.shutdown();
	}
    }
}
