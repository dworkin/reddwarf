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
