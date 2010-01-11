/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
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
            org.apache.mina.transport.socket.nio.SocketAcceptor 
                    minaSocketAcceptor =
                    new org.apache.mina.transport.socket.nio.SocketAcceptor(
                    numProcessors, executor);
            
            SocketAcceptorConfig socketConfig =
                                 minaSocketAcceptor.getDefaultConfig();
                
            socketConfig.setReuseAddress(Boolean.parseBoolean(
                    System.getProperty(REUSE_ADDRESS_PROPERTY,
                                       DEFAULT_REUSE_ADDRESS)));
	    
            minaAcceptor = minaSocketAcceptor;
        } else {
            minaAcceptor = new DatagramAcceptor(executor);
        }
        SocketAcceptor acceptor = new SocketAcceptor(this, minaAcceptor);
        logger.log(Level.FINE, "returning {0}", acceptor);
        return acceptor;
    }
}
