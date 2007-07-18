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
import java.util.concurrent.Executors;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.SimpleByteBufferAllocator;

abstract class AbstractSocketEndpoint {

    /** The socket address this endpoint encapsulates. */
    protected final SocketAddress address;

    /** The transport type this endpoint encapsulates. */
    protected final TransportType transportType;

    /** The {@code Executor} used by this endpoint's connector or acceptor. */
    protected final Executor executor;

    /** A MINA configuration parameter whose value may affect performance. */
    protected final int numProcessors;

    /** The default {@code Executor} for IO threads. */
    private static final Executor defaultExecutor =
        Executors.newCachedThreadPool(new DaemonThreadFactory());

    // Set default MINA ByteBuffer policies
    static {
        // Don't use timed-expiration buffer pools; just allocate new ones
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

        // Use heap buffers instead of direct buffers
        ByteBuffer.setUseDirectBuffers(false);
    }

    /**
     * Constructs an {@code AbstractSocketEndpoint} with the given
     * {@link TransportType}. This is the simplest way to create an
     * {@code AbstractSocketEndpoint}. The returned endpoint will use 
     * a cached pool of daemon threads as necessary for processing events.
     *
     * @param address the socket address to encapsulate
     * @param type the type of transport
     */
    protected AbstractSocketEndpoint(SocketAddress address, TransportType type)
    {
        this(address, type, defaultExecutor, 1);
    }

    /**
     * Constructs an {@code AbstractSocketEndpoint} with the given
     * TransportType using the given {@link Executor} for thread management.
     * The {@code numProcessors} parameter refers to the number of MINA
     * {@code SocketIOProcessors} to initially create. {@code numProcessors}
     * must be greater than 0.
     * <p>
     * (Note: A {@code SocketIOProcessor} is a MINA implementation detail
     * that controls the internal processing of the IO. It is exposed here
     * to allow clients the option to configure this value, which may aid
     * performance tuning).
     *
     * @param address the socket address to encapsulate
     * @param type the type of transport
     * @param executor An {@code Executor} specifying the threading policy
     * @param numProcessors the number of processors available on this
     *        system. This value must be greater than 0.
     *
     * @throws IllegalArgumentException if {@code numProcessors} is zero or
     *         negative.
     */
    protected AbstractSocketEndpoint(SocketAddress address,
            TransportType type, Executor executor, int numProcessors)
    {
        if (address == null || type == null || executor == null) {
            throw new NullPointerException("null arg");
        }
        if (numProcessors <= 0) {
            throw new IllegalArgumentException("numProcessors must be >= 1");
        }
        this.address = address;
        this.transportType = type;
        this.executor = executor;
        this.numProcessors = numProcessors;
    }

    /**
     * Returns the {@link SocketAddress} encapsulated by this endpoint.
     *
     * @return the {@code SocketAddress} encapsulated by this endpoint
     */
    public SocketAddress getAddress() {
        return address;
    }

    /**
     * Returns the {@link TransportType} encapsulated by this endpoint.
     *
     * @return the {@code TransportType} encapsulated by this endpoint
     */
    public TransportType getTransportType() {
        return transportType;
    }

    /**
     * Returns the {@link Executor} encapsulated by this endpoint.
     * 
     * @return the {@code Executor} encapsulated by this endpoint
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Returns the number of processors available to the MINA framework.
     * 
     * @return the number of processors available to the MINA framework
     */
    public int getNumProcessors() {
        return numProcessors;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getClass().getName() + "[" + getAddress() + "]";
    }
}
