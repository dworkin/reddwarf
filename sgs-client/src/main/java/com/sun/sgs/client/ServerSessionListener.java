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

import java.nio.ByteBuffer;

/**
 * A client's listener for handling messages sent from server to client and
 * for handling other connection-related events.
 * <p>
 * A {@code ServerSessionListener} for a client is notified in the
 * following cases: when the associated client is joined to a channel
 * ({@link #joinedChannel joinedChannel}), a message is received from the
 * server ({@link #receivedMessage receivedMessage}), a connection with
 * the server is being re-established ({@link #reconnecting reconnecting}),
 * a connection has been re-established ({@link #reconnected reconnected}),
 * or finally when the associated server session becomes disconnected,
 * gracefully or otherwise ({@link #disconnected disconnected}).
 * <p>
 * If a server session becomes disconnected, it can no longer be used to
 * send messages to the server. In this case, a client must log in again to
 * obtain a new server session to communicate with the server.
 */
public interface ServerSessionListener {

    /**
     * Notifies this listener that its associated client has joined the
     * specified {@code channel}, and returns a non-{@code null}
     * {@link ClientChannelListener} for that channel.
     * <p>
     * When a message is received on the specified channel, the returned
     * listener's {@link ClientChannelListener#receivedMessage
     * receivedMessage} method is invoked with the specified channel and
     * the message.  The returned listener <i>is</i> notified of messages
     * that its client sends on the specified channel; that is, a sender
     * receives its own broadcasts.
     * <p>
     * When the client associated with this server session leaves the
     * specified channel, the returned listener's {@link
     * ClientChannelListener#leftChannel leftChannel} method is invoked with
     * the specified channel.
     * 
     * @param channel a channel
     * @return a non-{@code null} listener for the specified channel
     */
    ClientChannelListener joinedChannel(ClientChannel channel);
    
    /**
     * Notifies this listener that the specified message was sent by the
     * server.
     * 
     * @param message a read-only {@link ByteBuffer} containing the message
     */
    void receivedMessage(ByteBuffer message);

    /**
     * Notifies this listener that requests to the server must be
     * suspended.  The associated server session may be reconnecting with
     * the same server or another server, or its requests may be
     * temporarily suspended.
     *
     * <p>Once this method returns, the associated server session should
     * not send any messages to the server until this listener's {@code
     * reconnected} method is invoked. Any messages sent during message
     * suspension and/or server reconnection are not guaranteed to be
     * received and/or processed by the server.
     * 
     * <p>If the associated server session is reconnecting and a connection
     * can be re-established with the server in a timely manner, this
     * listener's {@link #reconnected reconnected} method will be
     * invoked. Otherwise, if a connection cannot be re-established, this
     * listener's {@code disconnected} method will be invoked with {@code
     * false} indicating that the associated session is disconnected from
     * the server and the client must log in again.
     */
    void reconnecting();

    /**
     * Notifies this listener when the associated server session is
     * successfully reconnected.  After this method is invoked, the
     * associated session can resume sending messages to the server.
     */
    void reconnected();

    /**
     * Notifies this listener that the associated server session is
     * disconnected.
     *
     * <p>If {@code graceful} is {@code true}, the disconnection
     * was due to the associated client gracefully logging out; otherwise,
     * the disconnection was due to other circumstances, such as forced
     * disconnection.
     *
     * <p>Before this method is invoked, it is guaranteed that the listeners
     * of all {@code ClientChannel}s with this session as a member will
     * have their {@link ClientChannelListener#leftChannel leftChannel}
     * methods invoked.
     *
     * @param graceful {@code true} if disconnection was due to the
     *        associated client gracefully logging out, and
     *        {@code false} otherwise
     * @param reason a string indicating the reason this session was
     *        disconnected, or {@code null} if no reason was provided
     */
    void disconnected(boolean graceful, String reason);
}
