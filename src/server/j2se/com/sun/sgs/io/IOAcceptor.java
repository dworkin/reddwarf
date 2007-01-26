package com.sun.sgs.io;

import java.io.IOException;

/**
 * Passively initiates connections by listening on an {@link Endpoint} and
 * asynchronously notifies an associated {@link IOAcceptorListener} of
 * each accepted connection.
 * <p>
 * Once an {@code IOAcceptor} is listening, it will continue to accept
 * incoming connections until its {@link #shutdown} method is called.  An
 * {@code IOAcceptor} may not be reused once it has been shut down.
 *
 * @param <T> the address family encapsulated by this {@code IOAcceptor}'s
 *        associated {@link Endpoint}
 */
public interface IOAcceptor<T> {

    /**
     * Passively accept incoming connections on the associated
     * {@link Endpoint}.  This call may block until listening is enabled,
     * but does not block to wait for incoming connections.  Each accepted
     * connection will result in an asynchronous call to
     * {@link IOAcceptorListener#newHandle} on the given {@code listener}.
     *
     * @param listener the listener that will be notified of new connections
     *
     * @throws IOException if there was a problem binding to the
     *         {@code Endpoint}
     * @throws IllegalStateException if the {@code IOAcceptor} has been
     *         shut down or is already listening
     */
    void listen(IOAcceptorListener listener) throws IOException;

    /**
     * Passively accept incoming connections on the associated
     * {@link Endpoint}.  This call may block until listening is enabled,
     * but does not block to wait for incoming connections.  Each accepted
     * connection will result in an asynchronous call to
     * {@link IOAcceptorListener#newHandle} on the given {@code listener}.
     * A new instance of the given {@code filterClass}, created from its
     * default no-arg constructor, will be attached to the connection.
     *
     * @param listener the listener that will be notified of new connections
     * @param filterClass the concrete class of {@link IOFilter} instances
     *        that will be attached to accepted connections
     *
     * @throws IOException if there was a problem binding to the
     *         {@code Endpoint}
     * @throws IllegalStateException if the {@code IOAcceptor} has been
     *         shut down or is already listening
     */
    void listen(IOAcceptorListener listener,
                Class<? extends IOFilter> filterClass) throws IOException;

    /**
     * Returns the {@link Endpoint} on which this {@code IOAcceptor} is
     * listening.
     *
     * @return the {@link Endpoint} on which this {@code IOAcceptor} is
     *         listening
     */
    Endpoint<T> getEndpoint();

    /**
     * Shuts down this {@code IOAcceptor}, releasing any resources in use.
     * Once shutdown, an {@code IOAcceptor} cannot be restarted.
     *
     * @throws IllegalStateException if the {@code IOAcceptor} has been
     *         shut down or was not listening
     */
    void shutdown();

}
