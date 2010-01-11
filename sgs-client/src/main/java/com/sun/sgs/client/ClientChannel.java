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
 * Represents a client's view of a channel.  A channel is a
 * communication group, consisting of multiple clients and the server.
 * <p>
 * The server is solely responsible for creating channels and
 * adding and removing clients from channels.  If desired, a client
 * can request that a channel be created or its session be joined to or
 * removed from the channel by sending an application-specific message to
 * the server (using its {@link ServerSession}).
 * <p>
 * When the server adds a client session to a channel, the client's
 * {@link ServerSessionListener}'s {@link
 * ServerSessionListener#joinedChannel joinedChannel} method is
 * invoked with that client channel, returning the client's
 * {@link ClientChannelListener} for the channel.  A
 * {@code ClientChannelListener} for a client channel is notified
 * as follows:
 * <ul>
 * <li>When a message is received on a client channel, the listener's
 * {@link ClientChannelListener#receivedMessage receivedMessage}
 * method is invoked with the channel and the message.  The listener
 * <i>is</i> notified of messages that its client sends on its associated
 * channel; that is, a sender receives its own broadcasts.</li>
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
     * Sends the message contained in the specified {@code ByteBuffer} to
     * this channel.  The message starts at the buffer's current position
     * and ends at the buffer's limit.  The buffer's position is not
     * modified by this operation.
     *
     * <p>If the server-side application does not filter messages on this
     * channel, the message will be delivered unaltered to all channel
     * members, including the sender. However, the server-side application
     * may <i>alter the message</i>, <i>discard the message</i>,
     * or <i>modify the list of recipients</i> for application-specific
     * reasons.  If the channel message is not delivered to the sender
     * (because it is discarded by the application, for example), the
     * sender's {@link ClientChannelListener} will not receive a {@link
     * ClientChannelListener#receivedMessage receivedMessage} notification
     * for that message.
     *
     * <p>The {@code ByteBuffer} may be reused immediately after this method
     * returns.  Changes made to the buffer after this method returns will
     * have no effect on the message sent to the channel by this invocation.
     *
     * @param message a message to send
     *
     * @throws IllegalStateException if the sender is not a member of
     *         this channel
     * @throws IOException if a synchronous I/O problem occurs
     */
    void send(ByteBuffer message) throws IOException;
}
