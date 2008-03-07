/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.io.ServerEndpoint;
import com.sun.sgs.io.AcceptorListener;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.ConnectionListener;

/**
 * This is an implementation of an {@link Acceptor} that uses a MINA 
 * {@link IoAcceptor} to accept incoming connections.
 * <p>
 * Its constructor is package-private, so use
 * {@link ServerEndpoint#createAcceptor} to create an instance.
 * This implementation is thread-safe.
 */
class SocketAcceptor implements Acceptor<SocketAddress> {

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
     * delivered on the connections that it accepts.
     */
    public void listen(AcceptorListener listener) 
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
    static final class AcceptHandler extends SocketConnectionListener {
        
        /** The AcceptorListener for our parent Acceptor. */
        private final AcceptorListener acceptorListener;
        
        /**
         * Constructs a new {@code AcceptHandler} with an
         * {@code AcceptorListener} that will be notified as new
         * connections arrive.
         * 
         * @param listener the listener to be notified of incoming
         *        connections
         */
        public AcceptHandler(AcceptorListener listener) {
            this.acceptorListener = listener;
        }
        
        /**
         * As new MINA {@code IoSession}s come in, set up a
         * {@code SocketConnection} and notify the associated
         * {@code AcceptorListener}. A new {@code CompleteMessageFilter}
         * instance will be attached to the new connection.
         * 
         * @param session the newly created {@code IoSession}
         */
        @Override
        public void sessionCreated(IoSession session) throws Exception {
            logger.log(Level.FINE, "accepted session {0}", session);
            CompleteMessageFilter filter = new CompleteMessageFilter();
            ConnectionListener connListener = acceptorListener.newConnection();
            SocketConnection connection =
                new SocketConnection(connListener, filter, session);
            session.setAttachment(connection);
        }
    }
}
