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

import org.apache.mina.common.IoConnector;
import org.apache.mina.transport.socket.nio.DatagramConnector;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.io.Endpoint;
import com.sun.sgs.io.Connector;

/**
 * An implementation of {@link Endpoint} that wraps a {@link SocketAddress}.
 */
public class SocketEndpoint extends AbstractSocketEndpoint
        implements Endpoint<SocketAddress>
{
    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketEndpoint.class.getName()));

    /**
     * Constructs a {@code SocketEndpoint} with the given
     * {@link TransportType}. This is the simplest way to create a
     * {@code SocketEndpoint}. The returned endpoint will use 
     * new daemon threads as necessary for processing events.
     *
     * @param address the socket address to encapsulate
     * @param type the type of transport
     */
    public SocketEndpoint(SocketAddress address, TransportType type) {
        super(address, type);
    }

    /**
     * Constructs a {@code SocketEndpoint} with the given TransportType
     * using the given {@link Executor} for thread management.
     * 
     * @param address the socket address to encapsulate
     * @param type the type of transport
     * @param executor an {@code Executor} specifying the threading policy
     */
    public SocketEndpoint(SocketAddress address,
                          TransportType type, Executor executor)
    {
        this(address, type, executor, 1);
    }

    /**
     * Constructs a {@code SocketEndpoint} with the given TransportType
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
    public SocketEndpoint(SocketAddress address, TransportType type,
            Executor executor, int numProcessors)
    {
        super(address, type, executor, numProcessors);
    }

    /**
     * {@inheritDoc}
     */
    public Connector<SocketAddress> createConnector() {
        IoConnector minaConnector;
        if (transportType.equals(TransportType.RELIABLE)) {
            minaConnector =
                new org.apache.mina.transport.socket.nio.SocketConnector(
                    numProcessors, executor);
        } else {
            minaConnector = new DatagramConnector(executor);
        }
        SocketConnector connector = new SocketConnector(this, minaConnector);
        logger.log(Level.FINE, "returning {0}", connector);
        return connector;
    }
}
