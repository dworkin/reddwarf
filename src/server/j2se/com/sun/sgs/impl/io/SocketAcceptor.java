package com.sun.sgs.impl.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.Endpoint;
import com.sun.sgs.io.IOAcceptorListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandler;

/**
 * This is an implementation of an {@link IOAcceptor} that uses a MINA 
 * {@link IoAcceptor} to accept incoming connections.
 * <p>
 * Its constructor is package-private, so use {@link Endpoint#createAcceptor}
 * to create an instance. This implementation is thread-safe.
 * 
 * @author  Sten Anderson
 * @since   1.0
 */
public class SocketAcceptor implements IOAcceptor<SocketAddress> {
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketAcceptor.class.getName()));
    
    /** The associated Mina acceptor that handles the binding. */
    private final IoAcceptor acceptor;

    /** The endpoint on which to listen. */
    private final SocketEndpoint endpoint;

    /** Whether this acceptor has been shutdown. */
    private volatile boolean shutdown = false;

    /**
     * Constructs a {@code SocketAcceptor} with the given MINA
     * {@link IoAcceptor}.
     * 
     * @param acceptor the MINA {@code IoAcceptor} will use for the underlying
     *        IO processing.
     */
    SocketAcceptor(SocketEndpoint endpoint, IoAcceptor acceptor) {
        this.endpoint = endpoint;
        this.acceptor = acceptor;
    }
    
    /**
     * {@inheritDoc}
     */
    public void listen(IOAcceptorListener listener, 
                    Class<? extends IOFilter> filterClass) throws IOException
    {
        checkShutdown();
        AcceptHandler acceptHandler =
            new AcceptHandler(listener,
                    (filterClass == null)
                    ? PassthroughFilter.class
                    : filterClass);
        acceptor.bind(endpoint.getAddress(), acceptHandler);
        logger.log(Level.FINE, "listening on {0}", getEndpoint());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses a {@code PassthroughFilter} which simply
     * forwards any data on untouched.
     */
    public void listen(IOAcceptorListener listener) 
        throws IOException
    {
        listen(listener, PassthroughFilter.class);
    }
    
    /**
     * {@inheritDoc}
     */
    public SocketEndpoint getEndpoint() {
        checkShutdown();

        Set<?> boundAddresses = acceptor.getManagedServiceAddresses();
        if (boundAddresses.size() != 1) {
            logger.log(Level.WARNING,
                    "Expected 1 bound address, got {0}",
                    boundAddresses.size());
        }
        SocketAddress sockAddr =
            (SocketAddress) boundAddresses.iterator().next();
        return new SocketEndpoint(sockAddr,
                endpoint.getTransportType(),
                endpoint.getExecutor(),
                endpoint.getNumProcessors());
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        // TODO currently allow multiple calls to shutdown; should we
        // only allow one? -JM
        shutdown = true;
        acceptor.unbindAll();
    }
    
    /**
     * Check whether this acceptor has been shutdown, throwing
     * IllegalStateException if it has.
     *
     * @throws IllegalStateException if this acceptor has been shutdown.
     */
    private void checkShutdown() {
        if (shutdown) {
            throw new IllegalStateException("Acceptor has been shutdown");
        }
    }
    
    /**
     * Internal adaptor class to handle events from the acceptor itself.
     * 
     * @author James Megquier
     */
    static final class AcceptHandler extends SocketHandler {
        
        /** The IOAcceptorListener for our parent IOAcceptor. */
        private final IOAcceptorListener listener;
        
        /** The class of IOFilter to attach to new connections. */
        private final Class<? extends IOFilter> filterClass;
        
        /**
         * Constructs a new {@code AcceptHandler} with an
         * {@code IOAcceptorListener} that will be notified as new
         * connections arrive.
         * 
         * @param listener the listener to be notified of incoming
         *        connections.
         * @param filterClass the type of filter to be attached to new
         *        handles
         */
        public AcceptHandler(IOAcceptorListener listener, 
                Class<? extends IOFilter> filterClass)
        {    
            this.listener = listener; 
            this.filterClass = filterClass;
        }
        
        /**
         * As new MINA {@code IoSession}s come in, set up a
         * {@code SocketHandle} and notify the associated
         * {@code IOAcceptorListener}. A new instance of the associated
         * filter will be attached to the new handle.
         * 
         * @param session the newly created {@code IoSession}
         */
        @Override
        public void sessionCreated(IoSession session) throws Exception {
            logger.log(Level.FINE, "accepted session {0}", session);
            IOFilter filter = filterClass.newInstance();
            IOHandler handler = listener.newHandle();
            SocketHandle handle = new SocketHandle(handler, filter, session);
            session.setAttachment(handle);
        }
    }
}
