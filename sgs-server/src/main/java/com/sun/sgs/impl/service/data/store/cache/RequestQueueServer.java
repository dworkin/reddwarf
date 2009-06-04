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

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import static com.sun.sgs.impl.util.DataStreamUtil.writeString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Properties;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * Implements the server side of a queue of requests, ignoring duplicate
 * requests after a network failure.  Requests are numbered between {@code 0}
 * and the configured maximum request value.  To insure the correct handling of
 * duplicate requests, the client side should insure that no more than the
 * configured maximum outstanding requests are outstanding at one time.
 *
 * @param	<R> the type of request
 */
public class RequestQueueServer<R extends Request> {

    /**
     * The property for specifying the largest request number, which must be at
     * least {@code 2} and not larger than {@value java.lang.Short#MAX_VALUE}.
     */
    public static final String MAX_REQUEST_PROPERTY = "max.request";

    /** The default maximum request number. */
    public static final int DEFAULT_MAX_REQUEST = Short.MAX_VALUE;

    /**
     * The property for specifying the largest number of requests that can be
     * outstanding, which must be at least {@code 1} and less than the value
     * specified for the largest request number.
     */
    public static final String MAX_OUTSTANDING_PROPERTY = "max.outstanding";

    /** The default maximum number of outstanding requests. */
    public static final int DEFAULT_MAX_OUTSTANDING = 10000;

    /** The logger for this class. */
    static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(RequestQueueServer.class.getName()));

    /** Handler for reading and performing requests. */
    final Request.RequestHandler<R> requestHandler;

    /** The largest request number. */
    final int maxRequest;

    /** The largest number of outstanding requests. */
    final int maxOutstanding;

    /**
     * The current connection, or {@code null} if not currently connected.
     * Synchronize on this object when accessing.
     */
    private Connection connection = null;

    /**
     * The number of the last request processed.  Synchronize on this object
     * when accessing.
     */
    private int lastRequest;

    /**
     * Creates an instance of this class.
     *
     * @param	requestHandler the handler for reading and performing requests
     * @param	properties additional configuration properties
     */
    public RequestQueueServer(
	Request.RequestHandler<R> requestHandler, Properties properties)
    {
	checkNull("requestHandler", requestHandler);
	this.requestHandler = requestHandler;
	PropertiesWrapper wrappedProperties =
	    new PropertiesWrapper(properties);
	maxRequest = wrappedProperties.getIntProperty(
	    MAX_REQUEST_PROPERTY, DEFAULT_MAX_REQUEST, 2, Short.MAX_VALUE);
	maxOutstanding = wrappedProperties.getIntProperty(
	    MAX_OUTSTANDING_PROPERTY, DEFAULT_MAX_OUTSTANDING, 1,
	    maxRequest - 1);
	lastRequest = maxRequest;
    }

    /** Shuts down the server. */
    public synchronized void shutdown() {
	if (connection != null) {
	    connection.disconnect();
	    connection = null;
	}
    }

    /**
     * Handles requests from a newly connected socket.
     *
     * @param	socket the socket
     * @throws	IOException if there is a problem accessing the socket's input
     *		or output streams
     */
    public synchronized void handleConnection(Socket socket)
	throws IOException
    {
	checkNull("socket", socket);
	if (connection != null) {
	    connection.disconnect();
	}
	connection = new Connection(socket);
    }

    /**
     * Determines if the first request number is earlier than the second one,
     * given the constraints on the maximum request number and the number of
     * requests that can be outstanding.
     *
     * @param	request1 the first request to compare
     * @param	request2 the second request to compare
     * @return	{@code true} if {@code request1} is earlier than {@code
     *		request2}, else {@code false}
     * @throws	IllegalArgumentException if either argument is negative or
     *		greater than the maximum request size
     */
    public boolean earlierRequest(int request1, int request2) {
	if (request1 < 0 || request1 > maxRequest) {
	    throw new IllegalArgumentException("Illegal request: " + request1);
	} else if (request2 < 0 || request2 > maxRequest) {
	    throw new IllegalArgumentException("Illegal request: " + request2);
	}
	int diff = (request1 - request2) % maxRequest;
	if (diff < 0) {
	    diff += maxRequest;
	}
	return diff != 0 && diff > maxOutstanding;
    }

    /** Returns the number of the last request processed. */
    synchronized int getLastRequest() {
	return lastRequest;
    }

    /** Sets the number of the last request processed. */
    synchronized void setLastRequest(int lastRequest) {
	this.lastRequest = lastRequest;
    }

    /** Handles a new socket connection. */
    private class Connection extends Thread {

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
	    start();
	}

	/** Disconnects this connection. */
	void disconnect() {
	    synchronized (this) {
		if (!disconnect) {
		    disconnect = true;
		    interrupt();
		}
	    }
	    while (true) {
		try {
		    join();
		    break;
		} catch (InterruptedException e) {
		}
	    }
	}

	/**
	 * Checks whether the connection should disconnect.
	 *
	 * @return	whether the connection should disconnect
	 */
	private synchronized boolean getDisconnectRequested() {
	    return disconnect;
	}

	/**
	 * Handles requests on the socket, returning when the connection is
	 * disconnected.
	 */
	public void run() {
	    int last = getLastRequest();
	    try {
		while (!getDisconnectRequested()) {
		    short requestNumber = in.readShort();
		    if (earlierRequest(last, requestNumber)) {
			R request = requestHandler.readRequest(in);
			try {
			    requestHandler.performRequest(request);
			    out.writeBoolean(true);
			} catch (Throwable t) {
			    out.writeBoolean(false);
			    writeString(t.getClass().getName(), out);
			    writeString(t.getMessage(), out);
			}
			last = requestNumber;
		    }
		}
	    } catch (IOException e) {
		if (!getDisconnectRequested() && logger.isLoggable(WARNING)) {
		    logger.logThrow(
			FINE, e,
			"RequestQueueServer connection closed on I/O failure");
		}
	    } catch (Throwable t) {
		if (logger.isLoggable(WARNING)) {
		    logger.logThrow(
			WARNING, t,
			"RequestQueueServer connection failed with" +
			" unexpected exception");
		}
	    } finally {
		setLastRequest(last);
		try {
		    socket.close();
		} catch (IOException e) {
		}
	    }
	}
    }
}
