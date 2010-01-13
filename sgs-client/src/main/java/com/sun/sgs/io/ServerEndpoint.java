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
 * Represents an abstract local communication endpoint. Implementations of
 * {@code ServerEndpoint} encapsulate the passive connection initiation
 * mechanism for particular address families (such as
 * {@link java.net.SocketAddress}).
 * <p>
 * Passive connection initiation is accomplished by obtaining a
 * {@code ServerEndpoint}'s {@link Acceptor} via {@link #createAcceptor}.
 *
 * @param <T> the address family encapsulated by this {@code ServerEndpoint}
 */
public interface ServerEndpoint<T> {

    /**
     * Creates an {@link Acceptor} to passively listen for connections
     * on this local {@code ServerEndpoint}.
     *
     * @return an {@code Acceptor} configured to listen on this
     *         {@code ServerEndpoint}
     *
     * @throws IOException if an acceptor cannot be created
     */
    Acceptor<T> createAcceptor() throws IOException;

    /**
     * Returns the address of type {@code T} encapsulated by this
     * {@code ServerEndpoint}.
     *
     * @return the address encapsulated by this {@code ServerEndpoint}
     */
    T getAddress();

}
