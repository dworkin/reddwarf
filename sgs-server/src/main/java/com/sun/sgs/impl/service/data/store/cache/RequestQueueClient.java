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
import static com.sun.sgs.impl.util.DataStreamUtil.readString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * A thread that implements the client side of a request queue.  The {@link
 * #addRequest addRequest} method adds requests, which are transmitted to the
 * server side and run there exactly once.  Requests for which no response has
 * been received are retransmitted to the server as needed after a network
 * failure.  Once a request has been performed, its {@link Request#completed
 * Request.completed} method will be called. <p>
 *
 * Here's the network protocol:
 *
 * <ul>
 * <li>Establish connection
 * <ul>
 * <li>Client request:<br>
 *	<code>(long) </code><i>nodeId</i>
 * <li>Server reply:<br>
 *	<code>(boolean) <b>true</b></code>
 * </ul>
 * <li>Request
 * <ul>
 * <li>Client request:<br>
 *	<code>(short) </code><i>requestNumber</i>,
 *	<code>(byte[]) </code><i>serializedRequest</i>
 * <li>Server reply:<br>
 *	// Success:<br>
 *	<code>(boolean) <b>true</b></code><br>
 *	// Failure:<br>
 *	<code>(boolean) <b>false</b></code>,
 *	<code>(String) </code><i>exceptionClassName</i>,
 *	<code>(String) </code><i>exceptionMessage</i>
 * </ul>
 * </ul>
 */
public final class RequestQueueClient extends Thread {

    /** The name of this class. */
    private static final String CLASSNAME = RequestQueueClient.class.getName();

    /** The logger for this class. */
    static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(CLASSNAME));

    /**
     * The property for specifying the maximum time in milliseconds to continue
     * attempting to make new connections and send requests when failures have
     * prevented any successful responses.
     */
    public static final String MAX_RETRY_PROPERTY = "max.retry";

    /** The default maximum retry time in milliseconds. */
    public static final long DEFAULT_MAX_RETRY = 1000;

    /**
     * The property for specifying the time in milliseconds to wait after a
     * connection failure before attempting to make a new connection.
     */
    public static final String RETRY_WAIT_PROPERTY = "retry.wait";

    /** The default retry wait time in milliseconds. */
    public static final long DEFAULT_RETRY_WAIT = 100;

    /**
     * The property for specifying the size of the queue that holds requests
     * waiting to be sent to the server.
     */
    public static final String REQUEST_QUEUE_SIZE_PROPERTY =
	"request.queue.size";

    /** The default request queue size. */
    public static final int DEFAULT_REQUEST_QUEUE_SIZE = 100;

    /**
     * The property for specifying the size of the queue of requests that have
     * been sent to the server but have not received responses.
     */
    public static final String SENT_QUEUE_SIZE_PROPERTY =
	"sent.queue.size";

    /** The default sent queue size. */
    public static final int DEFAULT_SENT_QUEUE_SIZE = 100;

    /** The node ID for this request queue. */
    private final long nodeId;

    /** The socket factory. */
    private final SocketFactory socketFactory;

    /** The object to call if the listener fails. */ 
    private final FailureReporter failureReporter;

    /** The maximum retry time in milliseconds. */
    private final long maxRetry;

    /** The retry wait time in milliseconds. */
    private final long retryWait;

    /** The queue of requests waiting to be sent. */
    final BlockingDeque<Request> requests;

    /**
     * The queue of requests, and their associated request numbers, that have
     * been sent to the server but the client has not received responses.
     */
    final BlockingDeque<RequestHolder> sentRequests;

    /** Requests from earlier connections that need to be resent, if any. */
    final List<RequestHolder> resendRequests = new ArrayList<RequestHolder>();

    /**
     * Whether the client has been told to shutdown.  Synchronize on this
     * thread when accessing.
     */
    private boolean shutdown;

    /**
     * The time in milliseconds of the first in the current run of failures
     * when attempting to make connections, or {@code -1} if the last attempt
     * succeeded.  Synchronize on this thread when accessing.
     */
    private long failureStarted = -1;

    /**
     * The current connection, or {@code null} if not currently connected.
     * Synchronize on this thread when accessing.
     */
    private Connection connection;

    /** 
     * The number of the next request to be sent.  Access to this field does
     * not need to be synchronized because it is only accessed by this thread.
     */
    private int nextRequest = 0;

    /**
     * Creates an instance of this class and starts the thread.  The {@link
     * FailureReporter#reportFailure reportFailure} method of {@code
     * failureReporter} will be called if the listener shuts itself down due to
     * repeated failures when attempting to connect to the request queue
     * server.  The failure reporter will not be called if a shutdown is
     * requested explicitly by a call to the {@link #shutdown} method.
     *
     * @param	nodeId the node ID associated with this request queue
     * @param	socketFactory the factory for creating sockets that connect to
     *		the server
     * @param	failureReporter the failure reporter
     * @param	properties additional configuration properties
     * @throws	IllegalArgumentException if {@code nodeId} is negative
     */
    public RequestQueueClient(long nodeId,
			      SocketFactory socketFactory,
			      FailureReporter failureReporter,
			      Properties properties)
    {
	super(CLASSNAME);
	if (nodeId < 0) {
	    throw new IllegalArgumentException(
		"The nodeId must not be negative: " + nodeId);
	}
	checkNull("socketFactory", socketFactory);
	checkNull("failureReporter", failureReporter);
	this.nodeId = nodeId;
	this.socketFactory = socketFactory;
	this.failureReporter = failureReporter;
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	maxRetry = wrappedProps.getLongProperty(
	    MAX_RETRY_PROPERTY, DEFAULT_MAX_RETRY, 1, Long.MAX_VALUE);
	retryWait = wrappedProps.getLongProperty(
	    RETRY_WAIT_PROPERTY, DEFAULT_RETRY_WAIT, 1, Long.MAX_VALUE);
	int requestQueueSize = wrappedProps.getIntProperty(
	    REQUEST_QUEUE_SIZE_PROPERTY, DEFAULT_REQUEST_QUEUE_SIZE,
	    1, Integer.MAX_VALUE);
	int sentQueueSize = wrappedProps.getIntProperty(
	    SENT_QUEUE_SIZE_PROPERTY, DEFAULT_SENT_QUEUE_SIZE, 1,
	    Integer.MAX_VALUE);
	requests = new LinkedBlockingDeque<Request>(requestQueueSize);
	sentRequests = new LinkedBlockingDeque<RequestHolder>(sentQueueSize);
	start();
	if (logger.isLoggable(FINE)) {
	    logger.log(FINE,
		       "Created RequestQueueClient" +
		       " nodeId:" + nodeId +
		       ", socketFactory:" + socketFactory +
		       ", maxRetry:" + maxRetry +
		       ", retryWait:" + retryWait +
		       ", requestQueueSize:" + requestQueueSize +
		       ", sentQueueSize:" + sentQueueSize);
	}
    }

    /**
     * Adds a request to the queue, waiting until the queue has space if it is
     * full.
     *
     * @param	request the request to add
     * @throws	IllegalStateException if the client has begun to shut down
     */
    public void addRequest(Request request) {
	checkNull("request", request);
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "RequestQueueClient nodeId:" + nodeId +
		       " adding request request:" + request);
	}
	boolean done = false;
	while (true) {
	    synchronized (this) {
		if (shutdown) {
		    throw new IllegalStateException(
			"The client has begun to shut down");
		}
	    }
	    if (done) {
		break;
	    }
	    try {
		requests.putLast(request);
		done = true;
	    } catch (InterruptedException e) {
	    }
	}
    }

    /** Shuts down the client, closing the existing connection, if any. */
    public void shutdown() {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "RequestQueueClient nodeId:" + nodeId +
		       " shutdown requested");
	}
	Connection c = null;
	synchronized (this) {
	    if (!shutdown) {
		shutdown = true;
		c = connection;
	    }
	}
	if (c != null) {
	    c.disconnect(true);
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
     * Creates connections to the server, sends requests, handles responses,
     * and returns when the client shuts down.
     */
    @Override
    public void run() {
	while (true) {
	    Connection c = null;
	    try {
		synchronized (this) {
		    if (shutdown) {
			break;
		    }
		    connection = new Connection();
		    c = connection;
		}
		c.handleConnection();
	    } catch (Throwable t) {
		noteConnectionException(t);
	    }
	}
	logger.log(FINE,
		   "RequestQueueClient nodeId:" + nodeId + " shut down");
    }

    /** A factory for creating connected sockets. */
    public interface SocketFactory {

	/**
	 * Creates a connected socket.
	 *
	 * @return	the socket
	 * @throws	IOException if there is a problem creating or
	 *		connecting the socket
	 */
	Socket createSocket() throws IOException;
    }

    /**
     * A basic implementation of {@code SocketFactory} that creates sockets
     * using a host name and port.
     */
    public static class BasicSocketFactory implements SocketFactory {

	/** The server host. */
	private final String host;

	/** The server port. */
	private final int port;

	/**
	 * Creates an instance of this class that creates a connected socket
	 * using the specified server host and port.
	 *
	 * @param	host the server host
	 * @param	port the server port
	 */
	public BasicSocketFactory(String host, int port) {
	    this.host = host;
	    this.port = port;
	}

	/** {@inheritDoc} */
	public Socket createSocket() throws IOException {
	    return new Socket(host, port);
	}

	/**
	 * Returns a string representation of this instance.
	 *
	 * @return	a string representation of this instance
	 */
	@Override
	public String toString() {
	    return "BasicSocketFactory[host:" + host + ", port:" + port + "]";
	}
    }

    /**
     * Notes that an exception was thrown when attempting to handle a
     * connection.
     */
    private synchronized void noteConnectionException(Throwable exception) {
	if (shutdown) {
	    return;
	}
	if (exception instanceof IOException) {
	    if (logger.isLoggable(FINER)) {
		logger.logThrow(FINER, exception,
				"RequestQueueClient nodeId:" + nodeId + 
				" connection closed for I/O exception");
	    }
	} else if (logger.isLoggable(WARNING)) {
	    logger.logThrow(WARNING, exception,
			    "RequestQueueClient nodeId:" + nodeId +
			    " connection closed for unexpected exception");
	}
	long now = System.currentTimeMillis();
	if (failureStarted == -1) {
	    failureStarted = now;
	}
	if (now > failureStarted + maxRetry) {
	    shutdown = true;
	    if (logger.isLoggable(WARNING)) {
		logger.logThrow(WARNING, exception,
				"RequestQueueClient nodeId:" + nodeId +
				" shutting down due to failure");
	    }
	    failureReporter.reportFailure(exception);
	} else {
	    while (System.currentTimeMillis() < now + retryWait) {
		try {
		    wait(retryWait);
		} catch (InterruptedException e) {
		}
	    }
	}
    }

    /** Notes that a connection was made successfully. */
    void noteConnected() {
	if (logger.isLoggable(FINER)) {
	    logger.log(FINER,
		       "RequestQueueClient nodeId:" + nodeId +
		       " connected successfully");
	}
	synchronized (this) {
	    failureStarted = -1;
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
    private static class RequestHolder {

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

	/** The socket. */
	private final Socket socket;

	/** The data output stream. */
	private final DataOutputStream out;

	/** The thread processing responses from the server. */
	private final ReceiveThread receiveThread;

	/** Whether this connection has been told to disconnect. */
	private volatile boolean disconnect;

	/**
	 * Creates an instance of this class.
	 *
	 * @throws	IOException if there is an error creating the socket, or
	 *		its input or output streams
	 */
	Connection() throws IOException {
	    /*
	     * Resend any requests sent earlier that have not yet been
	     * acknowledged.  Put these requests in front of ones already
	     * present since they should be sent first, making sure to maintain
	     * their order.
	     */
	    while (!sentRequests.isEmpty()) {
		resendRequests.add(0, sentRequests.removeLast());
	    }
	    socket = socketFactory.createSocket();
	    /* Configure the socket's input stream to timeout */
	    socket.setSoTimeout((int) maxRetry);
	    out = new DataOutputStream(
		new BufferedOutputStream(
		    socket.getOutputStream()));
	    receiveThread = new ReceiveThread(
		new DataInputStream(
		    new BufferedInputStream(
			socket.getInputStream())));
	    receiveThread.start();
	    if (logger.isLoggable(FINER)) {
		logger.log(FINER,
			   "RequestQueueClient nodeId:" + nodeId +
			   " created connection: " + socket);
	    }
	}

	/**
	 * Disconnects the connection, interrupting both the main and receive
	 * threads, and waiting for the receive thread to complete if
	 * requested.
	 *
	 * @param	waitForReceiveThread whether to wait for the receive
	 *		thread to complete
	 */
	void disconnect(boolean waitForReceiveThread) {
	    disconnect = true;
	    receiveThread.interrupt();
	    RequestQueueClient.this.interrupt();
	    if (waitForReceiveThread) {
		while (true) {
		    try {
			receiveThread.join();
			break;
		    } catch (InterruptedException e) {
		    }
		}
	    }
	}

	/**
	 * Sends requests to the server, returning when the connection is
	 * disconnected, and checking for failures in the {@link
	 * ReceiveThread}.
	 *
	 * @throws	Exception if a problem occurs writing requests to the
	 *		socket output stream or reading responses from the
	 *		socket input stream
	 */
	void handleConnection() throws Exception {
	    try {
		boolean first = true;
		while (!disconnect) {
		    if (first) {
			out.writeLong(nodeId);
			out.flush();
			first = false;
			if (logger.isLoggable(FINEST)) {
			    logger.log(FINEST,
				       "RequestQueueClient nodeId:" + nodeId +
				       " sent init connection");
			}
		    }
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
			if (logger.isLoggable(FINEST)) {
			    logger.log(FINEST,
				       "RequestQueueClient nodeId:" + nodeId +
				       " sending" +
				       " requestNumber:" + requestNumber +
				       ", request:" + request);
			}
			receiveThread.noteNewRequest();
			out.writeShort(requestNumber);
			sentRequests.putLast(
			    new RequestHolder(request, requestNumber));
			request.writeRequest(out);
			out.flush();
		    } catch (InterruptedException e) {
			break;
		    }
		}
		/* Throw exception seen by the receive thread, if any */
		Throwable receiveException =
		    receiveThread.getReceiveException();
		if (receiveException instanceof Exception) {
		    throw (Exception) receiveException;
		} else if (receiveException instanceof Error) {
		    throw (Error) receiveException;
		}
		assert receiveException == null;
	    } finally {
		try {
		    socket.close();
		} catch (IOException e) {
		}
	    }
	}

	/** A thread that processes server replies. */
	private class ReceiveThread extends Thread {

	    /** The data input stream. */
	    private final DataInput in;

	    /**
	     * The exception thrown when reading server input or {@code null}.
	     * Synchronize on this thread when accessing this field.
	     */
	    private Throwable receiveException;

	    /**
	     * The number of sent messages awaiting replies.  Synchronize on
	     * this thread when accessing this field.
	     */
	    private int pendingReplies = 0;

	    /**
	     * Creates an instance of this class.
	     *
	     * @param	in the data input stream
	     */
	    ReceiveThread(DataInput in) {
		super(CLASSNAME + ".receive");
		this.in = in;
	    }

	    /**
	     * Reads responses from the server and removes requests from the
	     * sent requests queue, returning when the connection is
	     * disconnected or when there is a problem reading the server
	     * responses.
	     */
	    @Override
	    public void run() {
		try {
		    /* Confirm that the connection has been established */
		    in.readBoolean();
		    boolean firstMessage = true;
		    while (!disconnect) {
			synchronized (this) {
			    if (pendingReplies == 0) {
				try {
				    wait();
				} catch (InterruptedException e) {
				}
				continue;
			    }
			}
			Throwable requestException = readResponse();
			if (firstMessage) {
			    noteConnected();
			    firstMessage = false;
			}
			RequestHolder holder = sentRequests.removeFirst();
			if (logger.isLoggable(FINEST)) {
			    logger.log(
				FINEST,
				"RequestQueueClient nodeId:" + nodeId +
				" received reply" +
				" requestNumber:" + holder.requestNumber +
				(requestException == null
				 ? " succeeded"
				 : " failed: " + requestException));
			}
			holder.request.completed(requestException);
			synchronized (this) {
			    pendingReplies--;
			}
		    }
		    return;
		} catch (Throwable t) {
		    setReceiveException(t);
		}
	    }

	    /**
	     * Reads a response from the server.
	     *
	     * @return	the exception thrown when performing the request on the
	     *		server, or {@code null} if no exception was thrown
	     * @throws	IOException if there was a I/O failure reading the
	     *		server response
	     * @throws	Exception if there was an unexpected failure reading
	     *		the server response
	     */
	    private Throwable readResponse() throws Exception {
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
		} catch (InvocationTargetException e) {
		    return e.getCause();
		}
	    }

	    /**
	     * Returns the exception that the receive thread got when reading
	     * responses from the server, or {@code null} if there was no
	     * exception.
	     *
	     * @return	the exception or {@code null}
	     */
	    synchronized Throwable getReceiveException() {
		return receiveException;
	    }

	    /**
	     * Sets the exception that the receive thread got when reading
	     * responses from the server.
	     */
	    private void setReceiveException(Throwable exception) {
		if (!disconnect) {
		    synchronized (this) {
			receiveException = exception;
		    }
		    disconnect(false);
		}
	    }

	    /**
	     * Notes that a new request has been made, notifying waiters that
	     * were waiting for a non-zero number of requests.
	     */
	    synchronized void noteNewRequest() {
		if (pendingReplies == 0) {
		    notifyAll();
		}
		pendingReplies++;
	    }
	}
    }
}
