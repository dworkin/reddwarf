package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoConnector;
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
 * TODO: make non-reusable? cancel pending conn on shutdown? -JM
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public class SocketConnector implements IOConnector
{
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketConnector.class.getName()));
    
    private final IoConnector connector;

    /**
     * Constructs a {@code SocketConnector} using the given {@code IoConnector}
     * for the underlying transport.  This constructor is only visible to the
     * package, so use one of the {@code ConnectorFactory.createConnector} 
     * methods to create a new instance.
     * 
     */
    SocketConnector(IoConnector connector) {
        this.connector = connector;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * A {@code PassthroughFilter} will be installed on the connected 
     * {@code IOHandle}, which simply passes the data on untouched.
     */
    public void connect(SocketAddress address, IOHandler handler) {
        connect(address, handler, new PassthroughFilter());
    }

    /**
     * {@inheritDoc}
     */
    public void connect(SocketAddress address, IOHandler handler, 
            IOFilter filter)
    {
        logger.log(Level.FINE, "connecting to {0}", address);
        connector.connect(address, new ConnectionHandler(handler, filter));
    }
    

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        logger.log(Level.FINE, "shutdown called");
    }

    static class ConnectionHandler extends SocketHandler {
        private final IOHandler handler;
        private final IOFilter filter;

        ConnectionHandler(IOHandler handler, IOFilter filter) {
            this.handler = handler;
            this.filter = filter;
        }

        public void sessionCreated(IoSession session) throws Exception {
            logger.log(Level.FINE, "created session {0}", session);
            SocketHandle handle = new SocketHandle(filter, session);
            handle.setIOHandler(handler);
        }
    }

}
