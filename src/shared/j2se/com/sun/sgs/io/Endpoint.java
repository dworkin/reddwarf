/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
