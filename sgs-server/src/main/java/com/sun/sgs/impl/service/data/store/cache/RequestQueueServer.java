/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.data.store.cache;

import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import static com.sun.sgs.impl.util.DataStreamUtil.writeString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * A thread that implements the server side of a queue of requests, ignoring
 * duplicate requests after a network failure.  Requests are numbered between
 * {@code 0} and {@value #MAX_REQUEST}.  To insure the correct handling of
 * duplicate requests, no more that {@value #MAX_OUTSTANDING} requests should
 * be allowed to be outstanding at one time.
 *
 * @param	<R> the type of request
 */
public class RequestQueueServer<R extends Request> extends Thread {

    /**
     * The largest supported request number, chosen so that it can be stored as
     * a {@code short}.
     */
    public static final int MAX_REQUEST = Short.MAX_VALUE;

    /**
     * The largest number of requests that should be outstanding, so we can
     * determine which requests are newer when the request number rolls over.
     */
    public static final int MAX_OUTSTANDING = 10000;

    /** Handler for reading and performing requests. */
    final Request.RequestHandler<R> requestHandler;

    /**
     * Whether the server has been told to shutdown.  Synchronize on this
     * thread when accessing.
     */
    private boolean shutdown;

    /**
     * The current connection, or {@code null} if not currently connected.
     * Synchronize on this thread when accessing.
     */
    private Connection connection;

    /**
     * The number of the last request processed.  Access to this field does not
     * need to be synchronized because it is only accessed by the {@code
     * RequestQueueServer} thread.
     */
    private int lastRequest;

    /**
     * Creates an instance of this class.
     *
     * @param	requestHandler the handler for reading and performing requests
     */
    public RequestQueueServer(Request.RequestHandler<R> requestHandler) {
	checkNull("requestHandler", requestHandler);
	this.requestHandler = requestHandler;
    }

    /** Shuts down the server. */
    public void shutdown() {
	synchronized (this) {
	    if (!shutdown) {
		shutdown = true;
		if (connection != null) {
		    connection.disconnect();
		}
		interrupt();
	    }
	}
	while (true) {
	    try {
		join();
	    } catch (InterruptedException e) {
	    }
	}
    }

    /**
     * Handles requests from a newly connected socket.
     *
     * @param	socket the socket
     * @throws	IllegalStateException if the server is shutdown
     * @throws	IOException if there is a problem accessing the socket's input
     *		or output streams
     */
    public synchronized void handleConnection(Socket socket)
	throws IOException
    {
	checkNull("socket", socket);
	boolean first = true;
	while (true) {
	    if (shutdown) {
		throw new IllegalStateException("Server is shutdown");
	    } else if (connection == null) {
		break;
	    } else if (first) {
		connection.disconnect();
		interrupt();
		first = false;
	    }
	    try {
		wait();
	    } catch (InterruptedException e) {
	    }
	}
	connection = new Connection(socket);
	notifyAll();
    }

    /**
     * Waits for new connections, handles requests on the connections, and
     * returns when the server is shutdown.
     */
    @Override
    public void run() {
	while (true) {
	    Connection c;
	    synchronized (this) {
		if (shutdown) {
		    break;
		} else if (connection == null) {
		    try {
			wait();
		    } catch (InterruptedException e) {
		    }
		    continue;
		} else {
		    c = connection;
		}
	    }
	    try {
		c.handleConnection();
	    } catch (IOException e) {
		/* FIXME: Decide if node has failed */
	    }
	}
    }

    /**
     * Determines if the first request number is earlier than the second one,
     * given the constraints on the maximum request number and the number of
     * requests that can be outstanding.
     *
     * @return	{@code true} if {@code request1} is earlier than {@code
     *		request2}, else {@code false}
     * @throws	IllegalArgumentException if either argument is negative or
     *		greater than {@value #MAX_REQUEST}
     */
    public static boolean earlierRequest(int request1, int request2) {
	if (request1 < 0 || request1 > MAX_REQUEST) {
	    throw new IllegalArgumentException("Illegal request: " + request1);
	} else if (request2 < 0 || request2 > MAX_REQUEST) {
	    throw new IllegalArgumentException("Illegal request: " + request2);
	}
	int diff = (request1 - request2) % MAX_REQUEST;
	if (diff < 0) {
	    diff += MAX_REQUEST;
	}
	return diff != 0 && diff > MAX_OUTSTANDING;
    }

    /** Handles a new socket connection. */
    private class Connection {

	/** The socket. */
	private final Socket socket;

	/** The data input stream. */
	private final DataInput in;

	/** The data output stream. */
	private final DataOutput out;

	/**
	 * Whether the connection has been told to disconnect.  Synchronize on
	 * this connection when accessing.
	 */
	private boolean disconnect;
	
	/**
	 * Creates an instance of this class.
	 *
	 * @param	socket the new socket
	 */
	Connection(Socket socket) throws IOException {
	    this.socket = socket;
	    in = new DataInputStream(
		new BufferedInputStream(
		    socket.getInputStream()));
	    out = new DataOutputStream(
		new BufferedOutputStream(
		    socket.getOutputStream()));
	}

	/** Requests that the connection be disconnected. */
	synchronized void disconnect() {
	    disconnect = true;
	}

	/**
	 * Checks if the connection should disconnect.
	 *
	 * @return	whether the connection should disconnect
	 */
	private synchronized boolean getDisconnectRequested() {
	    return disconnect;
	}

	/**
	 * Handles requests on the socket, returning when the connection is
	 * disconnected.
	 *
	 * @throws	IOException if a problem occurs reading requests from
	 *		the socket input stream, or writing responses to the
	 *		socket output stream
	 */
	void handleConnection() throws IOException {
	    try {
		while (!getDisconnectRequested()) {
		    short requestNumber = in.readShort();
		    if (earlierRequest(lastRequest, requestNumber)) {
			R request = requestHandler.readRequest(in);
			try {
			    requestHandler.performRequest(request);
			    out.writeBoolean(true);
			    lastRequest = requestNumber;
			} catch (Throwable t) {
			    out.writeBoolean(false);
			    writeString(t.getClass().getName(), out);
			    writeString(t.getMessage(), out);
			    lastRequest = requestNumber;
			}
		    }
		}
	    } finally {
		close();
	    }
	}

	/** Closes the connection's socket. */
	void close() {
	    try {
		socket.close();
	    } catch (IOException e) {
	    }
	}
    }
}
