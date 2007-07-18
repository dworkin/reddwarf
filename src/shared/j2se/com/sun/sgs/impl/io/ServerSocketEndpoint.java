/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.transport.socket.nio.DatagramAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.ServerEndpoint;

/**
 * An implementation of {@link ServerEndpoint} that wraps a
 * local {@link SocketAddress}.
 */
public class ServerSocketEndpoint extends AbstractSocketEndpoint
        implements ServerEndpoint<SocketAddress>
{
    /** The System property for whether to set the reuse-address property. */
    public static final String REUSE_ADDRESS_PROPERTY = 
        "com.sun.sgs.impl.io.ServerSocketEndpoint.reuseAddress";
    
    /** Default value for the reuse-address property if not specified. */
    public static final String DEFAULT_REUSE_ADDRESS = "true";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(
                ServerSocketEndpoint.class.getName()));

    /**
     * Constructs a {@code ServerSocketEndpoint} with the given
     * {@link TransportType}. This is the simplest way to create a
     * {@code ServerSocketEndpoint}. The returned endpoint will use 
     * new daemon threads as necessary for processing events.
     *
     * @param address the socket address to encapsulate
     * @param type the type of transport
     */
    public ServerSocketEndpoint(SocketAddress address, TransportType type) {
        super(address, type);
    }

    /**
     * Constructs a {@code ServerSocketEndpoint} with the given TransportType
     * using the given {@link Executor} for thread management.
     * 
     * @param address the socket address to encapsulate
     * @param type the type of transport
     * @param executor an {@code Executor} specifying the threading policy
     */
    public ServerSocketEndpoint(SocketAddress address, TransportType type,
            Executor executor) {
        this(address, type, executor, 1);
    }

    /**
     * Constructs a {@code ServerSocketEndpoint} with the given TransportType
     * using the given {@link Executor} for thread management.
     * The {@code numProcessors} parameter refers to the number of
     * MINA {@code SocketIOProcessors} to initially create.
     * {@code numProcessors} must be greater than 0.
     * <p>
     * (Note: A {@code SocketIOProcessor} is a MINA implementation detail that
     * controls the internal processing of the IO. It is exposed here to allow
     * clients the option to configure this value, which may aid
     * performance tuning).
     *
     * @param address the socket address to encapsulate
     * @param type the type of transport
     * @param executor An {@code Executor} specifying the threading policy
     * @param numProcessors the number of processors available on this
     *        system.  This value must be greater than 0.
     *
     * @throws IllegalArgumentException if {@code numProcessors} is
     *         zero or negative.
     */
    public ServerSocketEndpoint(SocketAddress address, TransportType type,
            Executor executor, int numProcessors)
    {
        super(address, type, executor, numProcessors);
    }

    /**
     * {@inheritDoc}
     */
    public Acceptor<SocketAddress> createAcceptor() {
        IoAcceptor minaAcceptor;
        
        if (transportType.equals(TransportType.RELIABLE)) {
            org.apache.mina.transport.socket.nio.SocketAcceptor minaSocketAcceptor = 
                new org.apache.mina.transport.socket.nio.SocketAcceptor(
                    numProcessors, executor);
            
            IoServiceConfig ioConfig = minaSocketAcceptor.getDefaultConfig();
            
            /**
             * Note: I assume that this object will always be of type
             * "SocketAcceptConfig," but the javadocs do not guarantee this so I
             * will be cautious and test first before casting it...
             */
            if (ioConfig instanceof SocketAcceptorConfig) {
                SocketAcceptorConfig socketConfig =
                    (SocketAcceptorConfig)ioConfig;
                
                socketConfig.setReuseAddress(Boolean.parseBoolean(
                        System.getProperty(REUSE_ADDRESS_PROPERTY,
                                           DEFAULT_REUSE_ADDRESS)));
            } else {
                logger.log(Level.WARNING,
                    "IoServiceConfig object from mina SocketAcceptor object" +
                    " was not a subclass of SocketAcceptorConfig as expected;" +
                           " setReuseAddress() was not called.  {0}", ioConfig);
            }
	    
            minaAcceptor = minaSocketAcceptor;
        } else {
            minaAcceptor = new DatagramAcceptor(executor);
        }
        SocketAcceptor acceptor = new SocketAcceptor(this, minaAcceptor);
        logger.log(Level.FINE, "returning {0}", acceptor);
        return acceptor;
    }
}
