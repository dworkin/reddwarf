package com.sun.sgs.io;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * Actively initiates a single connection to an {@link Endpoint} and
 * asynchronously notifies the associated {@link IOHandler} when the
 * connection completes.
 * <p>
 * A connection attempt may be terminated by a call to {@link #shutdown},
 * unless the connection has already finished connecting.  Once an
 * {@code IOConnector} has connected or shut down, it may not be reused.
 *
 * @param <T> the address family encapsulated by this {@code IOConnector}'s
 *        associated {@link Endpoint}
 *
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOConnector<T> {

    /**
     * Actively initate a connection to the associated {@link Endpoint}.
     * This call is non-blocking. {@link IOHandler#connected} will be
     * called asynchronously on the {@code listener} upon successful
     * connection, or {@link IOHandler#disconnected} if it fails.
     *
     * @param listener the listener for all IO events on the connection,
     *        including the result of the connection attempt.
     *
     * @throws IOException if there was a problem initating the connection.
     * @throws IllegalStateException if the {@code IOConnector} has been
     *         shut down or has already attempted a connection.
     */
    void connect(IOHandler listener);

    /**
     * Actively initate a connection to the associated {@link Endpoint}.
     * This call is non-blocking. {@link IOHandler#connected} will be
     * called asynchronously on the {@code listener} upon successful
     * connection, or {@link IOHandler#disconnected} if it fails.  The given
     * {@link IOFilter} will be attached to the {@link IOHandle} upon
     * connecting.
     *
     * @param listener the listener for all IO events on the connection,
     *        including the result of the connection attempt.
     * @param filter the {@link IOFilter} to attach to the connection.
     *
     * @throws IOException if there was a problem initating the connection.
     * @throws IllegalStateException if the {@code IOConnector} has been
     *         shut down or has already attempted a connection.
     */
    void connect(IOHandler listener, IOFilter filter);

    /**
     * Returns the {@link Endpoint} this {@code IOConnector} will connect to.
     *
     * @return the {@code Endpoint} this {@code IOConnector} will connect to.
     */
    Endpoint<T> getEndpoint();

    /**
     * Shuts down this {@code IOConnector}.  The pending connection attempt
     * will be cancelled.
     *
     * @throws IllegalStateException if there is no connection attempt in
     *         progress.
     */
    void shutdown();
}
