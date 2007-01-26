package com.sun.sgs.io;

import java.io.IOException;

/**
 * Represents an abstract communication endpoint. Implementations of
 * {@code Endpoint} encapsulate the connection-creation mechanism for
 * particular address families (such as {@link java.net.SocketAddress}).
 * <p>
 * Active or passive connection initiation is accomplished by obtaining an
 * {@code Endpoint}'s {@link IOConnector} or {@link IOAcceptor},
 * respectively.
 *
 * @param <T> the address family encapsulated by this {@code Endpoint}
 */
public interface Endpoint<T> {

    /**
     * Creates an {@link IOConnector} for actively initiating a connection
     * to this remote {@code Endpoint}.
     *
     * @return an {@code IOConnector} configured to connect to this
     *         {@code Endpoint}
     *
     * @throws IOException if a connector cannot be created
     */
    IOConnector<T> createConnector() throws IOException;

    /**
     * Creates an {@link IOAcceptor} to passively listen for connections
     * on this local {@code Endpoint}.
     *
     * @return an {@code IOAcceptor} configured to listen on this
     *         {@code Endpoint}
     *
     * @throws IOException if an acceptor cannot be created
     */
    IOAcceptor<T> createAcceptor() throws IOException;

    /**
     * Returns the address of type {@code T} encapsulated by this
     * {@code Endpoint}.
     *
     * @return the address encapsulated by this {@code Endpoint}
     */
    T getAddress();

}
