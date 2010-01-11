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
