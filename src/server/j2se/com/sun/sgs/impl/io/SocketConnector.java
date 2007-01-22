package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.Endpoint;
import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.io.IOConnector;

/**
 * This is a socket-based implementation of an {@code IOConnector} using
 * the Apache Mina framework for the underlying transport.  It uses an
 * {@link org.apache.mina.common.IoConnector} to initiate connections on 
 * remote hosts.
 * <p>
 * Its constructor is package-private, so use {@link Endpoint#createConnector}
 * to create an instance. This implementation is thread-safe.
 * 
 * @author  Sten Anderson
 * @since   1.0
 */
public class SocketConnector implements IOConnector<SocketAddress>
{
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketConnector.class.getName()));
    
    private final IoConnector connector;
    private ConnectionHandler connectionHandler = null;

    private final SocketEndpoint endpoint;
    /**
     * Constructs a {@code SocketConnector} using the given
     * {@code IoConnector} for the underlying transport. This constructor is
     * only visible to the package, so use one of the
     * {@code ConnectorFactory.createConnector} methods to create a new
     * instance.
     * 
     * @param endpoint the remote address to which to connect
     * @param connector the Mina IoConnector to use for establishing the
     *        connection
     */
    SocketConnector(SocketEndpoint endpoint, IoConnector connector) {
        this.connector = connector;
        this.endpoint = endpoint;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * A {@code PassthroughFilter} will be installed on the connected 
     * {@code IOHandle}, which simply passes the data on untouched.
     */
    public void connect(IOHandler handler) {
        connect(handler, new PassthroughFilter());
    }

    /**
     * {@inheritDoc}
     */
    public void connect(IOHandler handler, IOFilter filter)
    {
        synchronized (this) {
            if (connectionHandler != null) {
                RuntimeException e = new IllegalStateException(
                            "Connection already in progress");
                logger.logThrow(Level.FINE, e, e.getMessage());
                throw e;
            }
            connectionHandler = new ConnectionHandler(handler, filter);
        }
        logger.log(Level.FINE, "connecting to {0}", endpoint);
        connector.connect(endpoint.getAddress(), connectionHandler);
    }
    

    /**
     * {@inheritDoc}
     */
    public synchronized void shutdown() {
        logger.log(Level.FINE, "shutdown called");
        synchronized (this) {
            if (connectionHandler == null) {
                RuntimeException e = new IllegalStateException(
                            "No connection in progress");
                logger.logThrow(Level.FINE, e, e.getMessage());
                throw e;
            }
        }
        connectionHandler.cancel();
    }

    /**
     * Internal adaptor class to handle events from the connector itself.
     * 
     * @author James Megquier
     */
    static final class ConnectionHandler extends SocketHandler {
        /** The requested IOHandler for the connected session. */
        private final IOHandler handler;
        
        /** The IOFilter instance to attach to the new connection. */
        private final IOFilter filter;
        
        /** Whether this connector has been cancelled. */
        private boolean cancelled = false;
        
        /** Whether this connector has finished connecting. */
        private boolean connected = false;

        /**
         * Constructs a new {@code ConnectionHandler} with an
         * {@code IOHandler} that will handle events for the new connectin.
         * 
         * @param handler the handler for the completed connection
         * @param filter the IOFilter to attach to the connection
         */
        ConnectionHandler(IOHandler handler, IOFilter filter) {
            this.handler = handler;
            this.filter = filter;
        }
        
        /**
         * If a connection is in progress, but not yet connected,
         * cancel the pending connection.
         * 
         * @throw IllegalStateException if this connection attempt has
         *        already completed or been cancelled.
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
         * The connection is starting; set the IOHandler for it.
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
            SocketHandle handle = new SocketHandle(handler, filter, session);
            session.setAttachment(handle);
        }
    }

    /**
     * {@inheritDoc}
     */
    public SocketEndpoint getEndpoint() {
        return endpoint;
    }

}
