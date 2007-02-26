/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.io;

import java.io.IOException;

/**
 * Passively initiates connections by listening on a {@link ServerEndpoint}
 * and asynchronously notifies an associated {@link AcceptorListener} of
 * each accepted connection.
 * <p>
 * Once an {@code Acceptor} is listening, it will continue to accept
 * incoming connections until its {@link #shutdown} method is called.  An
 * {@code Acceptor} may not be reused once it has started listening or
 * been shut down.
 *
 * @param <T> the address family encapsulated by this {@code Acceptor}'s
 *        associated {@link ServerEndpoint}
 */
public interface Acceptor<T> {

    /**
     * Passively accepts incoming connections on the associated
     * {@link ServerEndpoint}.  This call may block until listening
     * succeeds, but does not block to wait for incoming connections.
     * Each accepted connection will result in an asynchronous call to
     * {@link AcceptorListener#newConnection newConnection} on the given
     * {@code listener}.
     *
     * @param listener the listener that will be notified of new connections
     *
     * @throws IOException if there was a problem listening on the
     *         {@code ServerEndpoint}
     * @throws IllegalStateException if the {@code Acceptor} has been
     *         shut down or is already listening
     */
    void listen(AcceptorListener listener) throws IOException;

    /**
     * Returns the {@link ServerEndpoint} this {@code Acceptor} was
     * created with.
     * 
     * @return the {@link ServerEndpoint} this {@code Acceptor} was
     *         created with
     */
    ServerEndpoint<T> getEndpoint();

    /**
     * Returns the {@link ServerEndpoint} on which this {@code Acceptor}
     * is listening.
     * 
     * @return the {@link ServerEndpoint} on which this {@code Acceptor}
     *         is listening
     *
     * @throws IllegalStateException if the {@code Acceptor} has been shut
     *         down or was not listening
     */
    ServerEndpoint<T> getBoundEndpoint();

    /**
     * Shuts down this {@code Acceptor}, releasing any resources in use.
     * Once shutdown, an {@code Acceptor} cannot be restarted.
     *
     * @throws IllegalStateException if the {@code Acceptor} has been
     *         shut down or was not listening
     */
    void shutdown();

}
