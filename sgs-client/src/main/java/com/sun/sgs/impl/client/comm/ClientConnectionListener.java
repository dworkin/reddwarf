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

package com.sun.sgs.impl.client.comm;

import com.sun.sgs.client.ServerSessionListener;

/**
 * Listener for communications events that occur on an associated
 * {@link ClientConnection}.
 */
public interface ClientConnectionListener {

    /**
     * Notifies this listener that a transport connection has
     * been established.
     *
     * @param connection the newly-established connection
     */
    void connected(ClientConnection connection);

    /**
     * Notifies this listener that protocol data has arrived from the
     * server.
     *
     * @param message protocol-specific message data
     */
    void receivedMessage(byte[] message);

    /**
     * Notifies this listener that a new session has successfully logged in.
     *
     * @param message protocol-specific session-start data
     *
     * @return the listener for events on the new
     * {@link com.sun.sgs.client.ServerSession}
     */
    ServerSessionListener sessionStarted(byte[] message);

    /**
     * Notifies this listener that its associated server connection is in
     * the process of reconnecting with the server.
     * <p>
     * If a connection can be re-established with the server in a
     * timely manner, this listener's {@link #reconnected reconnected}
     * method will be invoked.  Otherwise, if a connection cannot be
     * re-established, this listener's {@link #disconnected disconnected}
     * method will be invoked with {@code false} indicating that
     * the associated session has been disconnected from the server
     * and the client must log in again.
     *
     * @param message protocol-specific reconnection data
     */
    void reconnecting(byte[] message);

    /**
     * Notifies this listener that a reconnection effort was successful.
     *
     * @param message protocol-specific reconnection data
     */
    void reconnected(byte[] message);
    
    /**
     * Notifies this listener that the associated server connection is
     * disconnected.
     * <p>
     * If {@code graceful} is {@code true}, the disconnection was due to
     * the associated client gracefully logging out; otherwise, the
     * disconnection was due to other circumstances, such as forced
     * disconnection or network failure.
     *
     * @param graceful {@code true} if disconnection was part of graceful
     *        logout, otherwise {@code false}
     * @param message protocol-specific reconnection data
     */
    void disconnected(boolean graceful, byte[] message);

}
