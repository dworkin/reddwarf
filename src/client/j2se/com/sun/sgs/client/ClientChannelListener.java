/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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

/**
 * Listener for events relating to a {@link ClientChannel}.
 * <p>
 * When the server adds a client session to a channel, the client's
 * {@link ServerSessionListener}'s {@link
 * ServerSessionListener#joinedChannel joinedChannel} method is invoked with
 * that client channel, returning the client's {@code ClientChannelListener}
 * for the channel. A {@code ClientChannelListener} for a client channel
 * is notified as follows:
 * <ul>
 * <li>When a message is received on a client channel, the listener's
 * {@link ClientChannelListener#receivedMessage receivedMessage} method is
 * invoked with the channel, the sender's session identifier, and the
 * message. A {@code null} sender indicates that the message was sent
 * by the server. The listener is <i>not</i> notified of messages that its
 * client sends on its associated channel.</li>
 * <li> When the associated client leaves a channel, the listener's
 * {@link ClientChannelListener#leftChannel leftChannel} method is invoked
 * with the channel. Once a client has been removed from a channel, that
 * client can no longer send messages on that channel.</li>
 * </ul>
 */
public interface ClientChannelListener {

    /**
     * Notifies this listener that the specified message, sent by the
     * specified sender, was received on the specified channel. If the
     * specified sender is {@code null}, then the specified message
     * was sent by the server.
     * 
     * @param channel a client channel
     * @param sender sender's session identifier, or {@code null}
     * @param message a byte array containing a message
     */
    void receivedMessage(ClientChannel channel,
                         SessionId sender,
                         byte[] message);

    /**
     * Notifies this listener that the associated client was removed from
     * the specified channel. The associated client can no longer send
     * messages on the specified channel.
     * 
     * @param channel a client chanel
     */
    void leftChannel(ClientChannel channel);
}
