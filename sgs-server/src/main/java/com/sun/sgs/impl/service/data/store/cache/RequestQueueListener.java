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
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/** A thread for managing a socket server that handles incoming connections. */
public abstract class RequestQueueListener extends Thread {

    /**
     * The property for specifying the maximum time in milliseconds to continue
     * trying to accept and dispatch connections in the presence of failures
     * without any successful connections.
     */
    public static final String MAX_RETRY_PROPERTY = "max.retry";

    /** The default maximum retry time in milliseconds. */
    public static final long DEFAULT_MAX_RETRY = 1000;

    /**
     * The property for specifying the time in milliseconds to wait after a
     * connection failure before attempting to accept connections again.
     */
    public static final String RETRY_WAIT_PROPERTY = "retry.wait";

    /** The default retry wait time in milliseconds. */
    public static final long DEFAULT_RETRY_WAIT = 100;

    /** The logger for this class. */
    private static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(RequestQueueListener.class.getName()));

    /** The socket server. */
    private final ServerSocket serverSocket;

    /** A runnable to call if the listener fails. */ 
    private final Runnable failureHandler;

    /** The maximum retry time in milliseconds. */
    private final long maxRetry;

    /** The retry wait time in milliseconds. */
    private final long retryWait;

    /** Whether the listener should shutdown. */
    private boolean shutdown;

    /**
     * The time in milliseconds of the first in the current run of failures
     * when attempting to accept connections, or {@code -1} if the last attempt
     * succeeded.
     */
    private long failureStarted = -1;

    /**
     * The exception that caused the listener to be shutdown, or {@code null}.
     */
    private Throwable failureException;

    /**
     * Creates an instance of this class.  The {@link Runnable#run run} method
     * of {@code failureHandler} will be called if the listener is shutdown due
     * to repeated failures when attempting to accept connections.
     *
     * @param	serverSocket the server socket for accepting connections
     * @param	failureHandler the failure handler
     * @param	properties additional configuration properties
     * @throws	IOException if there is a failure creating the server socket 
     */
    protected RequestQueueListener(ServerSocket serverSocket,
				   Runnable failureHandler,
				   Properties properties)
	throws IOException
    {
	checkNull("serverSocket", serverSocket);
	checkNull("failureHandler", failureHandler);
	this.serverSocket = serverSocket;
	this.failureHandler = failureHandler;
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	maxRetry = wrappedProps.getLongProperty(
	    MAX_RETRY_PROPERTY, DEFAULT_MAX_RETRY, 1, Long.MAX_VALUE);
	retryWait = wrappedProps.getLongProperty(
	    RETRY_WAIT_PROPERTY, DEFAULT_RETRY_WAIT, 1, Long.MAX_VALUE);
    }

    /**
     * Returns the server responsible for handling connections from the
     * node with the specified node ID.
     *
     * @param	nodeId the node ID
     * @return	the server responsible for the node
     */
    abstract protected RequestQueueServer getServer(long nodeId);

    /** Shuts down the listener. */
    public void shutdown() {
	synchronized (this) {
	    if (!shutdown) {
		shutdown = true;
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
     * Accepts connections to the server socket, reads the node ID from the
     * socket input stream, and dispatches handling the socket to the
     * appropriate server, returning when the listener is shutdown.
     */
    @Override
    public synchronized void run() {
	try {
	    while (!shutdown) {
		try {
		    Socket socket = serverSocket.accept();
		    DataInputStream in =
			new DataInputStream(socket.getInputStream());
		    long nodeId = in.readLong();
		    getServer(nodeId).handleConnection(socket);
		    noteConnected();
		} catch (IOException e) {
		    if (!shutdown) {
			noteConnectionException(e);
		    }
		} catch (Throwable t) {
		    noteFailed(t);
		    break;
		}
	    }
	} finally {
	    try {
		serverSocket.close();
	    } catch (IOException e) {
	    }
	}
    }

    /**
     * Notes that an exception was thrown when attempting to handle a
     * connection.
     */
    private void noteConnectionException(Throwable exception) {
	assert Thread.holdsLock(this);
	if (logger.isLoggable(FINE)) {
	    logger.logThrow(
		FINE, exception, "RequestQueueListener connection failed");
	}
	long now = System.currentTimeMillis();
	if (failureStarted != -1) {
	    failureStarted = now;
	}
	if (now > failureStarted + maxRetry) {
	    noteFailed(exception);
	} else {
	    try {
		wait(retryWait);
	    } catch (InterruptedException e) {
	    }
	}
    }

    /** Notes that the listener has failed as a result of an exception. */
    private void noteFailed(Throwable exception) {
	assert Thread.holdsLock(this);
	shutdown = true;
	if (logger.isLoggable(WARNING)) {
	    logger.logThrow(WARNING, exception, "RequestQueueListener failed");
	}
	failureHandler.run();
    }

    /** Notes that a connection was accepted successfully. */
    private void noteConnected() {
	assert Thread.holdsLock(this);
	failureStarted = -1;
    }
}
