/*
 * Copyright 2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
