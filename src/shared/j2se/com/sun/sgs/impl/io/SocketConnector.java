/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.io.Endpoint;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.io.Connector;

/**
 * This is a socket-based implementation of an {@code Connector} using
 * the Apache MINA framework for the underlying transport.  It uses an
 * {@link org.apache.mina.common.IoConnector} to initiate connections on 
 * remote hosts.
 * <p>
 * Its constructor is package-private, so use {@link Endpoint#createConnector}
 * to create an instance. This implementation is thread-safe.
 */
class SocketConnector implements Connector<SocketAddress>
{
    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketConnector.class.getName()));
    
    private final IoConnector connector;
    private ConnectorConnListner connListener = null;

    private final SocketEndpoint endpoint;

    private ConnectFuture connectFuture;

    /**
     * Constructs a {@code SocketConnector} using the given
     * {@code IoConnector} for the underlying transport. This constructor is
     * only visible to the package, so use one of the
     * {@code ConnectorFactory.createConnector} methods to create a new
     * instance.
     * 
     * @param endpoint the remote address to which to connect
     * @param connector the {@link IoConnector MINA IoConnector} to use
     *        for establishing the connection
     */
    SocketConnector(SocketEndpoint endpoint, IoConnector connector) {
        this.connector = connector;
        this.endpoint = endpoint;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * This implementation ensures that only complete messages are
     * delivered on the connection that it connects.
     */
    public void connect(ConnectionListener listener)
    {
        synchronized (this) {
            if (connListener != null) {
                RuntimeException e = new IllegalStateException(
                            "Connection already in progress");
                logger.logThrow(Level.FINE, e, e.getMessage());
                throw e;
            }
            connListener = new ConnectorConnListner(listener);
        }
        logger.log(Level.FINE, "connecting to {0}", endpoint);
        connectFuture = connector.connect(endpoint.getAddress(), connListener);
    }

    /**
     * {@inheritDoc}
     */
    public synchronized boolean isConnected() {
	return connectFuture != null && connectFuture.isConnected();
    }

    /**
     * {@inheritDoc}
     */
    public boolean waitForConnect(long timeoutMillis) {
	ConnectFuture future;
	synchronized (this) {
	    future = connectFuture;
	}
	if (future == null) {
	    throw new IllegalStateException("No connection in progress");
	}
	if (! future.isConnected()) {
	    future.join(timeoutMillis);
	}
	return future.isReady();
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void shutdown() {
        logger.log(Level.FINE, "shutdown called");
        synchronized (this) {
            if (connListener == null) {
                RuntimeException e = new IllegalStateException(
                            "No connection in progress");
                logger.logThrow(Level.FINE, e, e.getMessage());
                throw e;
            }
        }
        connListener.cancel();
    }

    /**
     * Internal adaptor class to handle events from the connector itself.
     */
    static final class ConnectorConnListner extends SocketConnectionListener {

        /** The requested ConnectionListener for the connected session. */
        private final ConnectionListener listener;
        
        /** Whether this connector has been cancelled. */
        private boolean cancelled = false;
        
        /** Whether this connector has finished connecting. */
        private boolean connected = false;

        /**
         * Constructs a new {@code ConnectionHandler} with an
         * {@code ConnectionListener} that will handle events for the new
         * connection.
         * 
         * @param listener the ConnectionListener for the completed
         *        connection
         */
        ConnectorConnListner(ConnectionListener listener) {
            this.listener = listener;
        }
        
        /**
         * If a connection is in progress, but not yet connected,
         * cancel the pending connection.
         * 
         * @throws IllegalStateException if this connection attempt has
         *         already completed or been cancelled
         */
        void cancel() {
            synchronized (this) {
                if (connected) {
                    RuntimeException e = new IllegalStateException(
                                "Already connected");
                    logger.logThrow(Level.FINE, e, e.getMessage());
                    throw e;
                }
                if (cancelled) {
                    RuntimeException e = new IllegalStateException(
                                "Already cancelled");
                    logger.logThrow(Level.FINE, e, e.getMessage());
                    throw e;
                }
                cancelled = true;
            }
        }

        /**
         * The connection is starting; set the ConnectionListener for it.
         * 
         * @param session the newly created {@code IoSession}
         */
        @Override
        public void sessionCreated(IoSession session) throws Exception {
            synchronized (this) {
                if (cancelled) {
                    logger.log(Level.FINE,
                            "cancelled; ignore created session {0}",
                            session);
                    session.close();
                    return;
                }
                connected = true;
            }
            
            logger.log(Level.FINE, "created session {0}", session);
            CompleteMessageFilter filter = new CompleteMessageFilter();
            SocketConnection connection =
                new SocketConnection(listener, filter, session);
            session.setAttachment(connection);
        }
    }

    /**
     * {@inheritDoc}
     */
    public SocketEndpoint getEndpoint() {
        return endpoint;
    }

}
