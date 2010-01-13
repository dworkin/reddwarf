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
 * Represents an abstract remote communication endpoint. Implementations of
 * {@code Endpoint} encapsulate the active connection-creation mechanism for
 * particular address families (such as {@link java.net.SocketAddress}).
 * <p>
 * Active connection initiation is accomplished by obtaining an
 * {@code Endpoint}'s {@link Connector} via {@link #createConnector}.
 *
 * @param <T> the address family encapsulated by this {@code Endpoint}
 */
public interface Endpoint<T> {

    /**
     * Creates a {@link Connector} for actively initiating a connection
     * to this remote {@code Endpoint}.
     *
     * @return a {@code Connector} configured to connect to this
     *         {@code Endpoint}
     *
     * @throws IOException if a connector cannot be created
     */
    Connector<T> createConnector() throws IOException;

    /**
     * Returns the address of type {@code T} encapsulated by this
     * {@code Endpoint}.
     *
     * @return the address encapsulated by this {@code Endpoint}
     */
    T getAddress();

}
