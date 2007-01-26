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
     * Actively initates a connection to the associated {@link Endpoint}.
     * This call is non-blocking.
     * {@link ConnectionListener#connected connected} will be called
     * asynchronously on the given {@code listener} upon successful
     * connection, or {@link ConnectionListener#disconnected disconnected}
     * if it fails.
     *
     * @param listener the listener for all IO events on the connection,
     *        including the result of the connection attempt
     *
     * @throws IOException if there was a problem initating the connection
     * @throws IllegalStateException if the {@code Connector} has been shut
     *         down or has already attempted a connection
     */
    void connect(ConnectionListener listener) throws IOException;

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
