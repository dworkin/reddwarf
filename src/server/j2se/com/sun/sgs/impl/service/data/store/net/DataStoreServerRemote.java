/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The server side of an experimental network protocol for implementing
 * DataStoreServer using sockets instead of RMI.
 */
/* XXX: Use thread pools? */
class DataStoreServerRemote implements Runnable {

    /* XXX: 2 hours -- same as RMI default.  Make configurable? */
    /** The number of milliseconds before closing an idle connection. */
    private static final int connectionReadTimeout = 2 * 3600 * 1000;

    /** The server socket, or null if closed. */
    ServerSocket serverSocket;

    /** The data store server, for up calls. */
    private final DataStoreServer server;

    /** Creates an instance for the specified server and port. */
    DataStoreServerRemote(DataStoreServer server, int port)
	throws IOException
    {
	serverSocket = new ServerSocket(port);
	this.server = server;
	new Thread(this, "DataStoreServerRemote").start();
    }

    /** Shuts down the server. */
    synchronized void shutdown() throws IOException {
	if (serverSocket != null) {
	    serverSocket.close();
	    serverSocket = null;
	}
    }

    /** Checks if the server is shut down. */
    private synchronized boolean isShutdown() {
	return serverSocket == null;
    }

    /** Accepts and hands off new connections until shut down. */
    public void run() {
	while (!isShutdown()) {
	    try {
		new Thread(
		    new Handler(serverSocket.accept()), "Handler").start();
	    } catch (Throwable t) {
	    }
	}
    }

    /** Handles connections. */
    private class Handler implements Runnable {

	/** The accepted socket. */
	private final Socket socket;

	/** Creates an instance for an accepted socket. */
	Handler(Socket socket) {
	    this.socket = socket;
	}

	/** Handles requests until an exception occurs. */
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
