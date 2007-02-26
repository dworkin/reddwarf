/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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
