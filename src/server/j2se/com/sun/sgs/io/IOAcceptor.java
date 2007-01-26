package com.sun.sgs.io;

import java.io.IOException;

/**
 * Passively initiates connections by listening on a {@link ServerEndpoint}
 * and asynchronously notifies an associated {@link IOAcceptorListener} of
 * each accepted connection.
 * <p>
 * Once an {@code IOAcceptor} is listening, it will continue to accept
 * incoming connections until its {@link #shutdown} method is called.  An
 * {@code IOAcceptor} may not be reused once it has started listening or
 * been shut down.
 *
 * @param <T> the address family encapsulated by this {@code IOAcceptor}'s
 *        associated {@link ServerEndpoint}
 */
public interface IOAcceptor<T> {

    /**
     * Passively accepts incoming connections on the associated
     * {@link ServerEndpoint}.  This call may block until listening
     * succeeds, but does not block to wait for incoming connections.
     * Each accepted connection will result in an asynchronous call to
     * {@link IOAcceptorListener#newHandle newHandle} on the given
     * {@code listener}.
     *
     * @param listener the listener that will be notified of new connections
     *
     * @throws IOException if there was a problem listening on the
     *         {@code ServerEndpoint}
     * @throws IllegalStateException if the {@code IOAcceptor} has been
     *         shut down or is already listening
     */
    void listen(IOAcceptorListener listener) throws IOException;

    /**
     * Returns the {@link ServerEndpoint} this {@code IOAcceptor} was
     * created with.
     * 
     * @return the {@link ServerEndpoint} this {@code IOAcceptor} was
     *         created with
     */
    ServerEndpoint<T> getEndpoint();

    /**
     * Returns the {@link ServerEndpoint} on which this {@code IOAcceptor}
     * is listening.
     * 
     * @return the {@link ServerEndpoint} on which this {@code IOAcceptor}
     *         is listening
     *
     * @throws IllegalStateException if the {@code IOAcceptor} has been shut
     *         down or was not listening
     */
    ServerEndpoint<T> getBoundEndpoint();

    /**
     * Shuts down this {@code IOAcceptor}, releasing any resources in use.
     * Once shutdown, an {@code IOAcceptor} cannot be restarted.
     *
     * @throws IllegalStateException if the {@code IOAcceptor} has been
     *         shut down or was not listening
     */
    void shutdown();

}
