/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.data.store.cache.queue;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import static com.sun.sgs.impl.util.DataStreamUtil.writeString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * Implements the server side of a request queue, using a {@link
 * Request.RequestHandler} to manage requests.  Requests are read from socket
 * input using a {@link DataInputStream}, their operations performed, and the
 * result written to socket output using a {@link DataOutputStream}. <p>
 *
 * First, the {@code boolean} {@code true} is written to the output stream, to
 * signify that the connection has been established successfully. <p>
 *
 * Each request number is read as a {@code short} from the input stream, with
 * the stream then supplied to the request handler's {@link
 * Request.RequestHandler#readRequest} method to read any additional data and
 * return the request.  If the request number is later than that of the last
 * request performed by the server, then the request handler's {@link
 * Request.RequestHandler#performRequest} method will be called to perform the
 * operation associated with the request.  If that method completes normally,
 * then {@code true} will be written to the output stream to tell the client
 * that the operation succeeded.  If the {@code performRequest} method throws
 * an exception, then {@code false} will be written to the output stream,
 * followed by the name of the exception class and the exception message.  Both
 * of these values are written by writing {@code false} if the value is {@code
 * null}, although the class name should never be {@code null}, or else {@code
 * true} followed by the UTF8 encoding of the string. <p>
 *
 * Requests are numbered between {@code 0} and the configured maximum request
 * value.  To insure the correct handling of duplicate requests, the client
 * side should insure that no more than the configured maximum outstanding
 * requests are outstanding at one time. <p>
 *
 * Note that duplicate requests received after a network failure are read, and
 * have a response written back, but are not performed.  Also, once performing
 * a request results in an exception, then that request and all future requests
 * will continue produce the same exception.
 *
 * @param	<R> the type of request
 * @see		RequestQueueClient
 * @see		RequestQueueListener
 */
public class RequestQueueServer<R extends Request> {

    /** The name of this class. */
    private static final String CLASSNAME = RequestQueueServer.class.getName();

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The maximum request number. */
    public static final int MAX_REQUEST = Short.MAX_VALUE;

    /**
     * The largest number of requests that can be outstanding, which must be
     * less than {@link #MAX_REQUEST}.
     */
    public static final int MAX_OUTSTANDING = 10000;

    /** The node ID for this server. */
    final long nodeId;

    /** Handler for reading and performing requests. */
    final Request.RequestHandler<R> requestHandler;

    /**
     * The current connection, or {@code null} if not currently connected.
     * Synchronize on this instance when accessing this field.
     */
    private Connection connection = null;

    /**
     * The number of the last request processed.  After initialization, this
     * field is only accessed by the {@link Connection#run} method, but making
     * it volatile insures that its value is propagated from one connection to
     * the next.
     */
    private volatile int lastRequest;

    /**
     * The exception thrown by the last request if there was one, else {@code
     * null}.  After initialization, this field is only accessed by the {@link
     * Connection#run} method, but making it volatile insures that its value is
     * propagated from one connection to the next.
     */
    private volatile Throwable lastRequestException = null;

    /**
     * Creates an instance of this class.
     *
     * @param	nodeId the node ID for this server
     * @param	requestHandler the handler for reading and performing requests
     * @throws	IllegalArgumentException if {@code nodeId} is negative
     */
    public RequestQueueServer(long nodeId,
			      Request.RequestHandler<R> requestHandler)
    {
	if (nodeId < 0) {
	    throw new IllegalArgumentException(
		"The nodeId must not be negative: " + nodeId);
	}
	this.nodeId = nodeId;
	checkNull("requestHandler", requestHandler);
	this.requestHandler = requestHandler;
	lastRequest = MAX_REQUEST;
	if (logger.isLoggable(FINER)) {
	    logger.log(FINER,
		       "Created RequestQueueServer" + " nodeId:" + nodeId);
	}
    }

    /** Disconnects the current connection, if any. */
    public synchronized void disconnect() {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "RequestQueueServer nodeId:" + nodeId +
		       " disconnect requested");
	}
	if (connection != null) {
	    connection.disconnect();
	    connection = null;
	}
    }

    /**
     * Handles requests from a newly connected socket, disconnecting the
     * current connection if one is present.
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
     * @param	x the first request to compare
     * @param	y the second request to compare
     * @return	{@code true} if {@code x} is earlier than {@code y}, else
     *		{@code false}
     * @throws	IllegalArgumentException if either argument is negative, if
     *		either argument is greater than the maximum request size, or if
     *		the values of the arguments imply that there are more than the
     *		maximum permitted number of requests outstanding
     */
    public boolean earlierRequest(int x, int y) {
	if (x < 0 || x > MAX_REQUEST) {
	    throw new IllegalArgumentException("Illegal request: " + x);
	} else if (y < 0 || y > MAX_REQUEST) {
	    throw new IllegalArgumentException("Illegal request: " + y);
	}
	int diff = (y - x) % (MAX_REQUEST + 1);
	if (diff < 0) {
	    diff += (MAX_REQUEST + 1);
	}
	if (diff == 0) {
	    return false;
	} else if (diff < MAX_OUTSTANDING) {
	    return true;
	} else if (diff > (MAX_REQUEST - MAX_OUTSTANDING)) {
	    return false;
	} else {
	    throw new IllegalArgumentException(
		"Too many requests outstanding: " +
		(MAX_REQUEST - MAX_OUTSTANDING));
	}
    }

    /** Handles a new socket connection. */
    private class Connection extends Thread {

	/** The socket. */
	private final Socket socket;

	/** The data input stream. */
	private final DataInput in;

	/** The data output stream. */
	private final DataOutputStream out;

	/** Whether the connection has been told to disconnect. */
	private volatile boolean disconnect;

	/**
	 * Creates an instance of this class and starts the thread.
	 *
	 * @param	socket the new socket
	 */
	Connection(Socket socket) throws IOException {
	    super(CLASSNAME + ".connection");
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
	    if (!disconnect) {
		disconnect = true;
		interrupt();
		try {
		    socket.close();
		} catch (IOException e) {
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
	 * Handles requests on the socket, returning when the connection is
	 * disconnected.
	 */
	public void run() {
	    try {
		out.writeBoolean(true);
		out.flush();
		while (!disconnect) {
		    short requestNumber = in.readShort();
		    R request = requestHandler.readRequest(in);
		    boolean newRequest =
			earlierRequest(lastRequest, requestNumber);
		    if (logger.isLoggable(FINEST)) {
			logger.log(FINEST,
				   "RequestQueueServer nodeId:" + nodeId +
				   " request received" +
				   " requestNumber:" + requestNumber +
				   ", request:" + request +
				   ", newRequest:" + newRequest);
		    }
		    /*
		     * Only perform later requests and only if the last request
		     * did not fail.
		     */
		    if (newRequest && lastRequestException == null) {
			try {
			    requestHandler.performRequest(request);
			} catch (Throwable t) {
			    lastRequestException = t;
			}
			lastRequest = requestNumber;
		    }
		    if (lastRequestException == null) {
			out.writeBoolean(true);
			out.flush();
			if (logger.isLoggable(FINEST)) {
			    logger.log(
				FINEST,
				"RequestQueueServer nodeId:" + nodeId +
				" request succeeded" +
				" requestNumber:" + requestNumber +
				", request:" + request);
			}
		    } else {
			out.writeBoolean(false);
			writeString(
			    lastRequestException.getClass().getName(), out);
			writeString(
			    lastRequestException.getMessage(), out);
			out.flush();
			if (logger.isLoggable(FINEST)) {
			    logger.logThrow(
				FINEST, lastRequestException,
				"RequestQueueServer nodeId:" + nodeId +
				" request throws" +
				" requestNumber:" + requestNumber +
				", request:" + request);
			}
		    }
		}
	    } catch (IOException e) {
		if (!disconnect && logger.isLoggable(FINER)) {
		    logger.logThrow(FINER, e,
				    "RequestQueueServer nodeId:" + nodeId +
				    " connection closed" +
				    " for I/O failure");
		}
	    } catch (Throwable t) {
		if (logger.isLoggable(WARNING)) {
		    logger.logThrow(WARNING, t,
				    "RequestQueueServer nodeId:" + nodeId +
				    " connection closed" +
				    " for unexpected exception");
		}
	    } finally {
		try {
		    socket.close();
		} catch (IOException e) {
		}
		if (logger.isLoggable(FINER)) {
		    logger.log(FINER,
			       "RequestQueueServer nodeId:" + nodeId +
			       " disconnected");
		}
	    }
	}
    }
}
