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

import static com.sun.sgs.impl.util.DataStreamUtil.readString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A thread that implements the client side of the request queue, retrying
 * pending requests after a network failure.
 */
public final class RequestQueueClient extends Thread {

    /** The server host name. */
    private final String host;

    /** The server port. */
    private final int port;

    /** A queue of requests waiting to be sent. */
    final BlockingDeque<Request> requests;

    /**
     * A queue of requests, and their associated request numbers, that have
     * been sent to the server but have not received responses.
     */
    final BlockingDeque<RequestHolder> sentRequests;

    /**
     * Whether the client has been told to shutdown.  Synchronize on this
     * thread when accessing.
     */
    private boolean shutdown;

    /**
     * The current connection, or {@code null} if not currently connected.
     * Synchronize on this thread when accessing.
     */
    private Connection connection;

    /** 
     * The number of the next request to be sent.  Access to this field does
     * not need to be synchronized because it is only accessed by the {@code
     * RequestQueueClient} thread.
     */
    private int nextRequest;

    /**
     * Creates an instance of this class.
     *
     * @param	host the server host name
     * @param	port the server port
     * @param	requestQueueSize the number of requests to buffer waiting to be
     *		sent
     * @param	sentQueueSize the number of requests to buffer waiting for
     *		replies from the server
     */
    public RequestQueueClient(
	String host, int port, int requestQueueSize, int sentQueueSize)
    {
	this.host = host;
	this.port = port;
	requests = new LinkedBlockingDeque<Request>(requestQueueSize);
	sentRequests = new LinkedBlockingDeque<RequestHolder>(sentQueueSize);
    }

    /**
     * Adds a request to the queue, returning {@code true} if successful and
     * {@code false} if the queue is full.
     *
     * @return	{@code true} if successful and {@code false} if the queue is
     *		full 
     * @throws	IllegalStateException if the client has been requested to
     *		shutdown 
     */
    public boolean addRequest(Request request) {
	synchronized (this) {
	    if (shutdown) {
		throw new IllegalStateException(
		    "The client has been requested to shutdown");
	    }
	    return requests.offerLast(request);
	}
    }

    /** Shuts down the client. */
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
     * Creates connections to the server, sends requests, handles responses,
     * and returns when the client is shut down.
     */
    @Override
    public void run() {
	while (true) {
	    try {
		Connection c;
		synchronized (this) {
		    if (shutdown) {
			break;
		    }
		    c = new Connection();
		    connection = c;
		}
		c.handleConnection();
	    } catch (IOException e) {
		/* FIXME: Decide if node has failed */
	    }
	}
    }

    /**
     * Returns the next request number, updating the {@code nextRequest} field
     * and wrapping around to {@code 0} as needed.
     *
     * @return	the next request number
     */
    short getNextRequestNumber() {
	int result = nextRequest++;
	if (nextRequest > Short.MAX_VALUE) {
	    nextRequest = 0;
	}
	return (short) result;
    }

    /** Stores a request and the associated request number. */
    private class RequestHolder {

	/** The request. */
	final Request request;

	/** The associated request number. */
	final short requestNumber;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	request the request
	 * @param	requestNumber the associated request number
	 */
	RequestHolder(Request request, short requestNumber) {
	    this.request = request;
	    this.requestNumber = requestNumber;
	}
    }

    /** Handles a new socket connection. */
    private class Connection {

	/** Requests from the previous connection that need to be resent. */
	private final List<RequestHolder> resendRequests;

	/** The socket. */
	private final Socket socket;

	/** The data output stream. */
	private final DataOutput out;

	/** The thread processing responses from the server. */
	private final ReceiveThread receiveThread;

	/**
	 * Whether this connection has been told to disconnect.  Synchronize on
	 * this connection when accessing.
	 */
	private boolean disconnect;

	/**
	 * Creates an instance of this class.
	 *
	 * @throws	IOException if there is an error creating the socket or
	 *		its input and output streams
	 */
	Connection() throws IOException {
	    resendRequests = new ArrayList<RequestHolder>(sentRequests);
	    sentRequests.clear();
	    socket = new Socket(host, port);
	    out = new DataOutputStream(
		new BufferedOutputStream(
		    socket.getOutputStream()));
	    /* FIXME: Install unhandled exception handler? */
	    receiveThread = new ReceiveThread(
		this,
		new DataInputStream(
		    new BufferedInputStream(
			socket.getInputStream())));
	}

	/**
	 * Requests that the connection be disconnected, and shuts down the
	 * receive thread.
	 */
	synchronized void disconnect() {
	    synchronized (this) {
		disconnect = true;
	    }
	    receiveThread.shutdown();
	}

	/**
	 * Checks if the connection should disconnect.
	 *
	 * @return	whether the connection should disconnect
	 */
	synchronized boolean getDisconnectRequested() {
	    return disconnect;
	}

	/**
	 * Sends requests to the server, returning when the connection is
	 * disconnected, and checking for failures in the {@link
	 * ReceiveThread}.
	 *
	 * @throws	IOException if a problem occurs writing requests to the
	 *		socket output stream or reading responses from the
	 *		socket input stream
	 */
	void handleConnection() throws IOException {
	    try {
		while (!getDisconnectRequested()) {
		    try {
			Request request;
			short requestNumber;
			if (!resendRequests.isEmpty()) {
			    RequestHolder holder = resendRequests.remove(0);
			    request = holder.request;
			    requestNumber = holder.requestNumber;
			} else {
			    request = requests.takeFirst();
			    requestNumber = getNextRequestNumber();
			}
			out.writeShort(requestNumber);
			sentRequests.putLast(
			    new RequestHolder(request, requestNumber));
			request.writeRequest(out);
		    } catch (InterruptedException e) {
			break;
		    }
		}
		IOException readException = receiveThread.getReadException();
		if (readException != null) {
		    throw readException;
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
	    receiveThread.shutdown();
	}
     }

    /** A thread that processes server replies. */
    private class ReceiveThread extends Thread {

	/** The associated connection, to disconnect on failure. */
	private final Connection connection;

	/** The data input stream. */
	private final DataInput in;

	/**
	 * An exception thrown because of a failure when reading the server
	 * response, or {@code null} if no failure has occurred.
	 */
	private IOException exception = null;

	private boolean shutdown;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	in the data input stream
	 */
	ReceiveThread(Connection connection, DataInput in) {
	    this.connection = connection;
	    this.in = in;
	}

	/** Shuts down this thread. */
	void shutdown() {
	    synchronized (this) {
		if (!shutdown) {
		    shutdown = true;
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
	 * Checks whether this thread should shutdown.
	 *
	 * @return	whether this thread should shutdown
	 */
	private synchronized boolean getShutdownRequested() {
	    return shutdown;
	}

	/**
	 * Reads responses from the server and removes requests from the sent
	 * requests queue, returning when the thread is shutdown or when there
	 * is a problem reading the server responses.
	 */
	@Override
	public void run() {
	    try {
		while (!getShutdownRequested()) {
		    Throwable exception;
		    RequestHolder holder;
		    try {
			exception = readResponse();
			holder = sentRequests.removeFirst();
		    } catch (IOException e) {
			setReadException(e);
			break;
		    } catch (NoSuchElementException e) {
			setReadException(
			    new IOException(
				"Received reply but no requests pending", e));
			break;
		    }
		    try {
			holder.request.completed(exception);
		    } catch (RuntimeException e) {
			/* Log spurious exception */
		    }
		}
	    } catch (Throwable t) {
		/* Log exception */
		connection.disconnect();
		RequestQueueClient.this.interrupt();
	    }
	}

	/**
	 * Notes that an exception was thrown when reading server responses.
	 *
	 * @param	exception the exception that was thrown
	 */
	private synchronized void setReadException(IOException exception) {
	    this.exception = exception;
	    connection.disconnect();
	    RequestQueueClient.this.interrupt();
	}

	/**
	 * Returns an exception thrown because of a failure when reading the
	 * server response, or {@code null} if no failure has occurred.
	 *
	 * @return	the exception or {@code null}
	 */
	synchronized IOException getReadException() {
	    return exception;
	}

	/**
	 * Reads a response from the server.
	 *
	 * @return	the exception thrown when performing the request on the
	 *		server, or {@code null} if no exception was thrown
	 * @throws	Exception if there was a failure reading the server
	 *		response 
	 */
	private Throwable readResponse() throws IOException {
	    if (in.readBoolean()) {
		return null;
	    }
	    String className = readString(in);
	    String message = readString(in);
	    try {
		Class<? extends Throwable> exceptionClass =
		    Class.forName(className).asSubclass(Throwable.class);
		Constructor<? extends Throwable> constructor
		    = exceptionClass.getConstructor(String.class);
		return constructor.newInstance(message);
	    } catch (Exception e) {
		return e;
	    }
	}
    }
}
