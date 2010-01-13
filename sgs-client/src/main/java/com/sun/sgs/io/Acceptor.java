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
