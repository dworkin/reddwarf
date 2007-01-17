package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.io.IOConnector;

/**
 * This is a socket-based implementation of an {@code IOConnector} using
 * the Apache Mina framework for the underlying transport.  It uses an
 * {@link org.apache.mina.common.IoConnector} to initiate connections on 
 * remote hosts.  
 * <p>
 * Its constructor is "package-private", so use one of the 
 * {@code ConnectorFactory.createConnector} methods and specify a 
 * {@code TransportType} to create a new instance. This implementation is 
 * thread-safe.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public class SocketConnector implements IOConnector
{
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketConnector.class.getName()));
    
    private final IoConnector connector;
    private ConnectionHandler connectionHandler = null;

    private final SocketAddress address;
    /**
     * Constructs a {@code SocketConnector} using the given {@code IoConnector}
     * for the underlying transport.  This constructor is only visible to the
     * package, so use one of the {@code ConnectorFactory.createConnector} 
     * methods to create a new instance.
     * 
     * @param address           the remote address to which to connect
     * @param connector         the Mina IoConnector to use for establishing
     *                          the connection
     */
    SocketConnector(SocketAddress address, IoConnector connector) {
        this.connector = connector;
        this.address = address;
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
        logger.log(Level.FINE, "connecting to {0}", address);
        connector.connect(address, connectionHandler);
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
    
    static final class ConnectionHandler extends IoHandlerAdapter {
        private final IOHandler handler;
        private final IOFilter filter;
        private boolean cancelled = false;
        private boolean connected = false;

        ConnectionHandler(IOHandler handler, IOFilter filter) {
            this.handler = handler;
            this.filter = filter;
        }
        
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
            SocketHandle handle = new SocketHandle(filter, session);
            handle.setIOHandler(handler);
        }
    }

}
