/*
 * Copyright 2007 Sun Microsystems, Inc.
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
 * Represents a connection for data communication, independent of
 * the QoS properties of the connection's transport or protocol.
 * An associated {@link ConnectionListener} is notified of asynchonous
 * events on the connection.
 */
public interface Connection {

    /**
     * Asynchronously sends data on this connection.
     * <p>
     * The specified byte array must not be modified after invoking this
     * method; if the byte array is modified, then this method may have
     * unpredictable results.
     *
     * @param message the message data to send
     *
     * @throws IOException if there was a synchronous problem 
     *         sending the message
     */
    void sendBytes(byte[] message) throws IOException;

    /**
     * Asynchronously closes this connection, freeing any resources
     * in use.  The connection should not be considered closed until
     * {@link ConnectionListener#disconnected disconnected} is invoked
     * on the {@linkplain ConnectionListener listener}.
     *
     * @throws IOException if there was a synchronous problem closing
     *         the connection
     */
    void close() throws IOException;

}
