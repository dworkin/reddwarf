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

import com.sun.sgs.impl.service.data.store.cache.FailureReporter;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * A listener for accepting and dispatching server connections of a request
 * queue.  When a socket connection is accepted, the listener will read a
 * {@code long}, which should not be negative, from the socket's input stream
 * to determine the node ID of the server that should handle the connection.
 * It passes the ID to the {@link ServerDispatcher} instance supplied to the
 * constructor to find the {@link RequestQueueServer}, and then passes the
 * socket to the server's {@link RequestQueueServer#handleConnection} method.
 * If attempts to accept sockets, read the node ID from the socket input
 * stream, or pass the socket to the server continue to fail, the listener will
 * shut down.
 *
 * @see		RequestQueueClient
 */
public final class RequestQueueListener extends Thread {

    /** The name of this class. */
    private static final String CLASSNAME =
	RequestQueueListener.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The socket server. */
    private final ServerSocket serverSocket;

    /** The object for finding the {@link RequestQueueServer}. */
    private final ServerDispatcher serverDispatcher;

    /** The object to call if the listener fails. */
    private final FailureReporter failureReporter;

    /** The maximum retry time in milliseconds. */
    private final long maxRetry;

    /** The retry wait time in milliseconds. */
    private final long retryWait;

    /**
     * Whether the listener should shutdown.  Synchronize on this instance when
     * accessing this field.
     */
    private boolean shutdown;

    /**
     * The time in milliseconds of the first in the current run of failures
     * when attempting to accept connections, or {@code -1} if the last attempt
     * succeeded.  Synchronize on this instance when accessing this field.
     */
    private long failureStarted = -1;

    /**
     * Creates an instance of this class and starts the thread.  The {@link
     * FailureReporter#reportFailure reportFailure} method of {@code
     * failureReporter} will be called if the listener shuts itself down due to
     * repeated failures when attempting to accept connections.  The failure
     * reporter will not be called if a shutdown is requested explicitly by a
     * call to the {@link #shutdown} method.  The server socket should already
     * be connected, and will be closed when the listener shuts down.
     *
     * @param	serverSocket the server socket for accepting connections
     * @param	serverDispatcher the object for finding the {@link
     *		RequestQueueServer}
     * @param	failureReporter the failure reporter
     * @param	maxRetry the maximum time in milliseconds to continue trying
     *		to accept and dispatch connections in the presence of failures
     *		without any successfully dispatched connections
     * @param	retryWait the time in milliseconds to wait after a
     *		connection failure before attempting to accept connections
     *		again
     */
    public RequestQueueListener(ServerSocket serverSocket,
				ServerDispatcher serverDispatcher,
				FailureReporter failureReporter,
				long maxRetry,
				long retryWait)
    {
	super(CLASSNAME);
	checkNull("serverSocket", serverSocket);
	checkNull("serverDispatcher", serverDispatcher);
	checkNull("failureReporter", failureReporter);
	if (maxRetry < 1) {
	    throw new IllegalArgumentException(
		"The maxRetry must not be less than 1");
	}
	if (retryWait < 1) {
	    throw new IllegalArgumentException(
		"The retryWait must not be less than 1");
	}
	this.serverSocket = serverSocket;
	this.serverDispatcher = serverDispatcher;
	this.failureReporter = failureReporter;
	this.maxRetry = maxRetry;
	this.retryWait = retryWait;
	start();
	if (logger.isLoggable(FINE)) {
	    logger.log(FINE,
		       "Created RequestQueueListener" +
		       " serverSocket:" + serverSocket +
		       ", maxRetry:" + maxRetry +
		       ", retryWait:" + retryWait);
	}
    }

    /** Shuts down the listener. */
    public void shutdown() {
	logger.log(FINEST, "RequestQueueListener shutdown requested");
	boolean doShutdown = false;
	synchronized (this) {
	    if (!shutdown) {
		shutdown = true;
		doShutdown = true;
	    }
	}
	if (doShutdown) {
	    interrupt();
	    try {
		serverSocket.close();
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
     * Accepts connections to the server socket, reads the node ID from the
     * socket input stream, and dispatches handling the socket to the
     * appropriate server, returning when the listener shuts down.
     */
    @Override
    public void run() {
	try {
	    while (true) {
		Socket socket = null;
		long nodeId = -1;
		try {
		    synchronized (this) {
			if (shutdown) {
			    break;
			}
		    }
		    socket = serverSocket.accept();
		    if (logger.isLoggable(FINEST)) {
			logger.log(
			    FINEST,
			    "RequestQueueListener accepted connection" +
			    " socket:" + socket);
		    }
		    DataInputStream in =
			new DataInputStream(socket.getInputStream());
		    nodeId = in.readLong();
		    if (logger.isLoggable(FINER)) {
			logger.log(
			    FINER,
			    "RequestQueueListener received init connection" +
			    " nodeId:" + nodeId +
			    ", socket:" + socket);
		    }
		    RequestQueueServer server =
			serverDispatcher.getServer(nodeId);
		    server.handleConnection(socket);
		    noteConnected(nodeId);
		} catch (Throwable t) {
		    if (socket != null) {
			try {
			    socket.close();
			} catch (IOException e) {
			}
		    }
		    noteConnectionException(nodeId, t);
		}
	    }
	} finally {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	    logger.log(FINE, "RequestQueueListener shut down");
	}
    }

    /**
     * The interface for finding the server responsible for handling
     * connections for a particular node.
     */
    public interface ServerDispatcher {

	/**
	 * Returns the server responsible for handling connections from the
	 * node with the specified node ID.
	 *
	 * @param	nodeId the node ID
	 * @return	the server responsible for the node
	 * @throws	IllegalArgumentException if no server is found for the
	 *		specified node ID
	 */
	RequestQueueServer<? extends Request> getServer(long nodeId);
    }

    /**
     * Notes that an exception was thrown when attempting to handle a
     * connection for the specified node, if known.
     */
    private synchronized void noteConnectionException(
	long nodeId, Throwable exception)
    {
	if (shutdown) {
	    return;
	}
	if (exception instanceof IOException) {
	    if (logger.isLoggable(FINER)) {
		logger.logThrow(
		    FINER, exception,
		    "RequestQueueListener nodeId:" +
		    (nodeId < 0 ? "unknown" : String.valueOf(nodeId)) +
		    " connection closed for I/O exception");
	    }
	} else if (logger.isLoggable(WARNING)) {
	    logger.logThrow(WARNING, exception,
			    "RequestQueueListener nodeId:" +
			    (nodeId < 0 ? "unknown" : String.valueOf(nodeId)) +
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
				"RequestQueueListener shutting down due" +
				" to failure");
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

    /**
     * Notes that a connection was accepted successfully for the specified
     * node.
     */
    private void noteConnected(long nodeId) {
	if (logger.isLoggable(FINER)) {
	    logger.log(FINER,
		       "RequestQueueListener nodeId:" + nodeId +
		       " connected successfully");
	}
	synchronized (this) {
	    failureStarted = -1;
	}
    }
}
