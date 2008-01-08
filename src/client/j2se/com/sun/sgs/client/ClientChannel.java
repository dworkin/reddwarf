/*
 * Copyright (c) 2008, Sun Microsystems, Inc.
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
 */

package com.sun.sgs.client;

import java.io.IOException;
import java.util.Set;

/**
 * Represents a client's view of a channel.  A channel is a
 * communication group, consisting of multiple clients and the server.
 * <p>
 * The server is solely responsible for creating channels and
 * adding and removing clients from channels.  If desired, a client
 * can request that a channel be created by sending an
 * application-specific message to the server (using its {@link
 * ServerSession}).
 * <p>
 * When the server adds a client session to a channel, the client's
 * {@link ServerSessionListener}'s {@link
 * ServerSessionListener#joinedChannel joinedChannel} method is
 * invoked with that client channel, returning the client's
 * {@code ClientChannelListener} for the channel.  A
 * {@code ClientChannelListener} for a client channel is notified
 * as follows:
 * <ul>
 * <li>When a message is received on a client channel, the listener's
 * {@link ClientChannelListener#receivedMessage receivedMessage}
 * method is invoked with the channel, the sender's session identifier,
 * and the message.  A {@code null} sender indicates that the
 * message was sent by the server.  The listener is <i>not</i>
 * notified of messages that its client sends on its associated
 * channel.</li>
 *
 * <li> When the associated client leaves a channel, the listener's
 * {@link ClientChannelListener#leftChannel leftChannel} method is
 * invoked with the channel.  Once a client has been removed
 * from a channel, that client can no longer send messages on that
 * channel.</li>
 * </ul>
 */
public interface ClientChannel {

    /**
     * Returns the name of this channel.  A channel's name is set when
     * it is created by the server-side application.
     *
     * @return the name of this channel
     */
    String getName();

    /**
     * Sends the given {@code message} to all channel members.  If this
     * channel has no members other than the sender, then no action is taken.
     * <p>
     * The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param message a message to send
     *
     * @throws IllegalStateException if the sender is not a member of
     *         this channel
     * @throws IOException if a synchronous IO problem occurs
     */
    void send(byte[] message) throws IOException;

    /**
     * Sends the given {@code message} to the {@code recipient} on this
     * channel.  If the {@code recipient} is not a member of this channel,
     * then no action is taken.
     * <p>
     * The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param recipient the channel member that should receive the message
     * @param message a message to send
     *
     * @throws IllegalStateException if the sender is not a member of
     *         this channel
     * @throws IOException if a synchronous IO problem occurs
     */
    void send(SessionId recipient, byte[] message) throws IOException;

    /**
     * Sends the given {@code message} data to the specified
     * {@code recipients} on this channel. Any {@code recipients} that are
     * not members of this channel are ignored.
     * <p>
     * The specified byte array must not be modified after invoking this
     * method; if the byte array is modified, then this method may have
     * unpredictable results.
     * 
     * @param recipients the subset of channel members that should receive
     *        the message
     * @param message a message to send
     *
     * @throws IllegalStateException if the sender is not a member of this
     *         channel
     * @throws IOException if a synchronous IO problem occurs
     */
    void send(Set<SessionId> recipients, byte[] message) throws IOException;

}
