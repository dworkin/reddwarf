package com.sun.sgs.io;

import java.io.IOException;

/**
 * Represents a communication endpoint with no transport or protocol
 * attachment. Implementations of {@code Endpoint} encapsulate the
 * connection-creation mechanism for particular address families (such as
 * {@link java.net.SocketAddress}).
 *
 * Active or passive connection initiation is accomplished by obtaining an
 * {@code Endpoint}'s {@link IOConnector} or {@link IOAcceptor},
 * respectively.
 *
 * @param <T> the address family encapsulated by this {@code Endpoint}
 *
 * @author Sten Anderson
 * @since 1.0
 */
public interface Endpoint<T> {

    /**
     * Creates a non-reusable connector for actively initiating a connection
     * to this remote {@code Endpoint}.
     *
     * @return an {@link IOConnector} configured to connect to this
     *         {@code Endpoint}.
     *
     * @throws IOException if a connector cannot be created.
     */
    IOConnector<T> createConnector() throws IOException;

    /**
     * Creates a non-reusable acceptor to passively listen for connections
     * on this local {@code Endpoint}.
     *
     * @return an {@link IOAcceptor} configured to listen on this
     *         {@code Endpoint}.
     *
     * @throws IOException if an acceptor cannot be created.
     */
    IOAcceptor<T> createAcceptor() throws IOException;

    /**
     * Return the address of type {@code T} encapsulated by this
     * {@code Endpoint}.
     *
     * @return the address encapsulated by this {@code Endpoint}.
     */
    T getAddress();
}
