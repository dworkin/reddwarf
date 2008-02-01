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
