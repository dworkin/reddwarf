/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.data.store.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The server side of an experimental network protocol, not currently used, for
 * implementing DataStoreServer using sockets instead of RMI.
 */
/*
 * XXX: Use thread pools?
 * XXX: Reap idle connections?
 */
class DataStoreServerRemote implements Runnable {

    /* XXX: 2 hours -- same as RMI default.  Make configurable? */
    /** The number of milliseconds before closing an idle connection. */
    private static final int connectionReadTimeout = 2 * 3600 * 1000;

    /** The server socket, or null if closed. */
    private ServerSocket serverSocket;

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

    /** Returns the local port. */
    synchronized int getLocalPort() throws IOException {
	if (serverSocket == null) {
	    throw new IOException("Server is shut down");
	}
	return serverSocket.getLocalPort();
    }

    /** Accepts and hands off new connections until shut down. */
    public void run() {
	while (true) {
	    ServerSocket ss;
	    synchronized (this) {
		ss = serverSocket;
	    }
	    if (ss == null) {
		break;
	    }
	    try {
		new Thread(
		    new Handler(ss.accept()), "Handler").start();
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
		setSocketOptions(socket);
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

	/** Sets TcpNoDelay, KeepAlive, and SoTimeout options, if possible. */
	private void setSocketOptions(Socket socket) {
	    try {
		socket.setTcpNoDelay(true);
	    } catch (Exception e) {
	    }
	    try {
		socket.setKeepAlive(true);
	    } catch (Exception e) {
	    }
	    if (connectionReadTimeout > 0) {
		try {
		    socket.setSoTimeout(connectionReadTimeout);
		} catch (Exception e) {
		}
	    }
	}
    }
}
