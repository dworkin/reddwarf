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

package com.sun.sgs.client;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents a client's view of a login session with the server. Each time
 * a client logs in, it will be assigned a different server session. A
 * client can use its {@code ServerSession} to send messages to the
 * server, to check if it is connected, or to log out.
 *
 * <p>A server session has an associated {@link ServerSessionListener} that
 * is notified of session communication events such as message receipt,
 * channel joins, reconnection, or disconnection. Once a server session is
 * disconnected, it can no longer be used to send messages to the
 * server. In this case, a client must log in again to obtain a new server
 * session to communicate with the server.
 */
public interface ServerSession {

    /**
     * Sends the message contained in the specified {@code ByteBuffer} to
     * the server.  The message starts at the buffer's current position and
     * ends at the buffer's limit.  The buffer's position is not modified
     * by this operation.  The specified message is sent asynchronously to
     * the server; therefore, a successful invocation of this method does
     * not indicate that the given message was successfully sent. Messages
     * that are received by the server are delivered in sending order.
     * 
     * <p>The {@code ByteBuffer} may be reused immediately after this method
     * returns.  Changes made to the buffer after this method returns will
     * have no effect on the message sent to the server by this invocation.
     *
     * @param message a message
     *
     * @throws IOException if this session is disconnected or an IO error
     *         occurs
     * @throws IllegalStateException if the client is not in an appropriate
     *	       state (suspended, for example) to send a message
     */
    void send(ByteBuffer message) throws IOException;

    /**
     * Returns {@code true} if this session is connected, otherwise
     * returns {@code false}.
     * 
     * @return {@code true} if this session is connected, and
     *         {@code false} otherwise
     */
    boolean isConnected();

    /**
     * Initiates logging out from the server. If {@code force} is
     * {@code true} then this session is forcibly terminated, for
     * example, by terminating the associated client's network connections.
     * If {@code force} is {@code false}, then this session
     * is gracefully disconnected, notifying the server that the client
     * logged out. When a session has been logged out, gracefully or
     * otherwise, the {@link ServerSessionListener#disconnected
     * disconnected} method is invoked on this session's associated {@link
     * ServerSessionListener} passing a {@code boolean} indicating
     * whether the disconnection was graceful.
     * <p>
     * If this server session is already disconnected, then an
     * {@code IllegalStateException} is thrown.
     * 
     * @param force if {@code true}, this session is forcibly
     *        terminated; otherwise the session is gracefully disconnected
     *
     * @throws IllegalStateException if this session is disconnected
     */
    void logout(boolean force);

}
