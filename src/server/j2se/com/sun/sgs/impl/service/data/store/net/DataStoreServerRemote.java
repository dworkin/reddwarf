package com.sun.sgs.impl.service.data.store.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The server side of a network protocol for implementing DataStoreServer using
 * sockets instead of RMI.
 */
/* XXX: Use thread pools? */
class DataStoreServerRemote implements Runnable {
    /* XXX: 2 hours -- same as RMI default */
    private static final int connectionReadTimeout = 2 * 3600 * 1000;
    ServerSocket serverSocket;
    private final DataStoreServer server;
    DataStoreServerRemote(DataStoreServer server, int port)
	throws IOException
    {
	serverSocket = new ServerSocket(port);
	this.server = server;
	new Thread(this, "DataStoreServerRemote").start();
    }
    synchronized void shutdown() throws IOException {
	if (serverSocket != null) {
	    serverSocket.close();
	    serverSocket = null;
	}
    }
    private synchronized boolean isShutdown() {
	return serverSocket == null;
    }
    public void run() {
	while (!isShutdown()) {
	    try {
		new Thread(
		    new Handler(serverSocket.accept()), "Handler").start();
	    } catch (Throwable t) {
	    }
	}
    }
    private class Handler implements Runnable {
	private final Socket socket;
	Handler(Socket socket) {
	    this.socket = socket;
	}
	public void run() {
	    try {
		try {
		    socket.setTcpNoDelay(true);
		} catch (Exception e) {
		}
		if (connectionReadTimeout > 0) {
		    try {
			socket.setSoTimeout(connectionReadTimeout);
		    } catch (Exception e) {
		    }
		}
		DataStoreProtocol protocol =
		    new DataStoreProtocol(
			socket.getInputStream(), socket.getOutputStream());
		while (true) {
		    protocol.dispatch(server);
		}
	    } catch (Throwable e) {
	    } finally {
		try {
		    socket.close();
		} catch (IOException e) {
		}
	    }
	}
    }
}
