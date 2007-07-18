/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

/**
 * Receives asynchronous notification of events from an associated
 * {@link Connection}. The {@code connected} method is invoked when the
 * connection is established, either actively from a {@link Connector} or
 * passively by an {@link Acceptor}. The {@code bytesReceived} method is
 * invoked when data arrives on the connection. The {@code exceptionThrown}
 * method is invoked to forward asynchronous network exceptions. The
 * {@code disconnected} method is invoked when the connection has been
 * closed, or if the connection could not be initiated at all (e.g., when a
 * connector fails to connect).
 * <p>
 * The IO framework ensures that only one notification is processed at a
 * time for each instance of {@code Connection}. Thus, a
 * {@code ConnectionListener} does not need to take special steps to ensure
 * thread safety so long as it does not access resources shared among the
 * listeners of other connections.
 */
public interface ConnectionListener {

    /**
     * Notifies this listener that the connection is
     * established, either actively from a {@link Connector} or passively
     * by an {@link Acceptor}. This indicates that the connection is
     * ready for use, and data may be sent and received on it.
     * 
     * @param conn the {@code Connection} that has become connected.
     */
    void connected(Connection conn);

    /**
     * Notifies this listener that data arrives on a connection.
     * The {@code message} is not guaranteed to be a single, whole
     * message; this method is responsible for message reassembly
     * unless the connection itself guarantees that only complete
     * messages are delivered.
     *
     * @param conn the {@code Connection} on which the message arrived
     * @param message the received message bytes
     */
    void bytesReceived(Connection conn, byte[] message);

    /**
     * Notifies this listener that a network exception has occurred
     * on the connection.
     *
     * @param conn the {@code Connection} on which the exception occured
     * @param exception the thrown exception
     */
    void exceptionThrown(Connection conn, Throwable exception);

    /**
     * Notifies this listener that the connection has been closed,
     * or that it could not be initiated (e.g., when a
     * {@code Connector} fails to connect).
     *
     * @param conn the {@code Connection} that has disconnected
     */
    void disconnected(Connection conn);

}
