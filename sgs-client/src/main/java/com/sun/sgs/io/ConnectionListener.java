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
