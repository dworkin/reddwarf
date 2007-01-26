package com.sun.sgs.impl.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.ServerEndpoint;
import com.sun.sgs.io.IOAcceptorListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOHandler;

/**
 * This is an implementation of an {@link IOAcceptor} that uses a MINA 
 * {@link IoAcceptor} to accept incoming connections.
 * <p>
 * Its constructor is package-private, so use
 * {@link ServerEndpoint#createAcceptor} to create an instance.
 * This implementation is thread-safe.
 */
class SocketAcceptor implements IOAcceptor<SocketAddress> {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketAcceptor.class.getName()));
    
    /** The associated MINA acceptor that handles the binding. */
    private final IoAcceptor acceptor;

    /** The endpoint on which to listen. */
    private final ServerSocketEndpoint endpoint;

    /** Whether this acceptor has been shutdown. */
    private volatile boolean shutdown = false;

    /**
     * Constructs a {@code SocketAcceptor} with the given MINA
     * {@link IoAcceptor}.
     *
     * @param endpoint the local address to which to listen
     * @param acceptor the MINA {@code IoAcceptor} to use for the underlying
     *        IO processing
     */
    SocketAcceptor(ServerSocketEndpoint endpoint, IoAcceptor acceptor) {
        this.endpoint = endpoint;
        this.acceptor = acceptor;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation ensures that only complete messages are
     * delivered on the handles that it accepts.
     */
    public void listen(IOAcceptorListener listener) 
        throws IOException
    {
        synchronized (this) {
            checkShutdown();
            AcceptHandler acceptHandler = new AcceptHandler(listener);
            acceptor.bind(endpoint.getAddress(), acceptHandler);
        }
        logger.log(Level.FINE, "listening on {0}", getBoundEndpoint());
    }

    /**
     * {@inheritDoc}
     */
    public ServerSocketEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * {@inheritDoc}
     */
    public ServerSocketEndpoint getBoundEndpoint() {
        synchronized (this) {
            checkShutdown();

            Set<?> boundAddresses = acceptor.getManagedServiceAddresses();
            if (boundAddresses.size() != 1) {
                logger.log(Level.WARNING,
                           "Expected 1 bound address, got {0}",
                           boundAddresses.size());
            }
            SocketAddress sockAddr =
                (SocketAddress) boundAddresses.iterator().next();
            return new ServerSocketEndpoint(sockAddr,
                                      endpoint.getTransportType(),
                                      endpoint.getExecutor(),
                                      endpoint.getNumProcessors());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        synchronized (this) {
            // TODO currently allow multiple calls to shutdown; should we
            // only allow one? -JM
            shutdown = true;
            acceptor.unbindAll();
        }
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
     */
    static final class AcceptHandler extends SocketHandler {
        
        /** The IOAcceptorListener for our parent IOAcceptor. */
        private final IOAcceptorListener listener;
        
        /**
         * Constructs a new {@code AcceptHandler} with an
         * {@code IOAcceptorListener} that will be notified as new
         * connections arrive.
         * 
         * @param listener the listener to be notified of incoming
         *        connections
         */
        public AcceptHandler(IOAcceptorListener listener) {
            this.listener = listener;
        }
        
        /**
         * As new MINA {@code IoSession}s come in, set up a
         * {@code SocketHandle} and notify the associated
         * {@code IOAcceptorListener}. A new {@code CompleteMessageFilter}
         * instance will be attached to the new handle.
         * 
         * @param session the newly created {@code IoSession}
         */
        @Override
        public void sessionCreated(IoSession session) throws Exception {
            logger.log(Level.FINE, "accepted session {0}", session);
            CompleteMessageFilter filter = new CompleteMessageFilter();
            IOHandler handler = listener.newHandle();
            SocketHandle handle = new SocketHandle(handler, filter, session);
            session.setAttachment(handle);
        }
    }
}
