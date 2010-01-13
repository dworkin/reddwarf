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

package com.sun.sgs.io;

import java.io.IOException;

/**
 * Actively initiates a single connection to an {@link Endpoint} and
 * asynchronously notifies the associated {@link ConnectionListener} when the
 * connection completes.
 * <p>
 * A connection attempt may be terminated by a call to {@link #shutdown},
 * unless the connection has already finished connecting.  Once a
 * {@code Connector} has connected or shut down, it may not be reused.
 *
 * @param <T> the address family encapsulated by this {@code Connector}'s
 *        associated {@link Endpoint}
 */
public interface Connector<T> {

    /**
     * Actively initiates a connection to the associated {@link Endpoint}.
     * This call is non-blocking.
     * {@link ConnectionListener#connected connected} will be called
     * asynchronously on the given {@code listener} upon successful
     * connection, or {@link ConnectionListener#disconnected disconnected}
     * if it fails.
     *
     * @param listener the listener for all IO events on the connection,
     *        including the result of the connection attempt
     *
     * @throws IOException if there was a problem initiating the connection
     * @throws IllegalStateException if the {@code Connector} has been shut
     *         down or has already attempted a connection
     */
    void connect(ConnectionListener listener) throws IOException;

    /**
     * Waits for the connect attempt, initiated by invoking the {@link
     * #connect connect} method, to complete with the given {@code
     * timeout} (specified in milliseconds), and returns {@code true}
     * if the connect attempt completed.
     *
     * <p>Use the {@link #isConnected isConnected} method on this
     * instance to determine if the connect attempt was successful.
     *
     * @param	timeout the wait timeout
     * @return	{@code true} if the connect attempt completed
     * @throws	IllegalStateException if no connect attempt is in progress
     * @throws	InterruptedException if the waiting thread is interrupted
     * @throws	IOException if the implementation determines that the connect
     *		attempt failed with an {@code IOException}
     */
    boolean waitForConnect(long timeout)
	throws IOException, InterruptedException;

    /**
     * Returns {@code true} if this connector is connected, otherwise
     * returns {@code false}.
     *
     * @return	{@code true} if this connector is connected
     */
    boolean isConnected();

    /**
     * Returns the {@link Endpoint} for this {@code Connector}.
     *
     * @return the {@code Endpoint} for this {@code Connector}
     */
    Endpoint<T> getEndpoint();

    /**
     * Shuts down this {@code Connector}.  The pending connection attempt
     * will be cancelled.
     *
     * @throws IllegalStateException if there is no connection attempt in
     *         progress
     */
    void shutdown();

}
