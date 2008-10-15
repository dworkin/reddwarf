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

package com.sun.sgs.protocol;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;

/**
 * A channel for sending protocol messages to a client.  A {@code
 * ProtocolMessageChannel} is created by the {@link
 * ProtocolFactory#newChannel ProtocolFactory.newChannel} method.
 *
 * <p>Note: If the protocol specification (implemented by a given {@code
 * ProtocolMessageChannel}) requires that a login acknowledgment be
 * delivered to the client before any other protocol messages, the protocol
 * message channel must implement this requirement.  It is possible that a
 * caller may request that other messages be sent before the login
 * acknowledgment, and if the protocol requires, these messages should be
 * enqueued until the login acknowledgment has been sent to the client.
 * 
 * <p>TBD: should reconnection be handled a this layer or transparently by
 * the transport layer?   Perhaps the {@code AsynchronousByteChannel}
 * managed by the transport layer could handle the reconnection under the
 * covers?
 */
public interface ProtocolMessageChannel extends Channel {

    /**
     * Notifies the associated client that it should redirect its login
     * redirect message to the specified {@code host} and {@code port}.
     *
     * @param	host a redirect host
     * @param	port a redirect port
     */
    void loginRedirect(String host, int port);

    /**
     * Notifies the associated client that the previous login attempt was
     * successful, and the client is assigned the given {@code sessionId}.
     *
     * @param	sessionId a session ID
     */
    void loginSuccess(BigInteger sessionId);

    /**
     * Notifies the associated client that the previous login attempt was
     * unsuccessful for the specified {@code reason}.  The specified {@code
     * throwable}, if non-{@code null} is an exception that occurred while
     * processing the login request.  The message channel should be careful
     * not to reveal to the associated client sensitive data that may be
     * present in the specified {@code throwable}.
     *
     * @param	reason a reason why the login was unsuccessful
     * @param	throwable an exception that occurred while processing the
     *		login request, or {@code null}
     */
    void loginFailure(String reason, Throwable throwable);

    /**
     * Sends the associated client the specified {@code message}.
     *
     * @param	message a message
     */
    void sessionMessage(ByteBuffer message);

    /**
     * Notifies the associated client that it is joined to the channel
     * with the specified {@code name} and {@code channelId}.
     *
     * @param	name a channel name
     * @param	channel a channelId
     */
    void channelJoin(String name, BigInteger channelId);

    /**
     * Notifies the associated client that it is no longer a member of
     * the channel with the specified {@code channelId}.
     *
     * @param	channelId a channel ID
     */
    void channelLeave(BigInteger channelId);

    /**
     * Notifies the associated client that the specified {@code message}
     * is sent on the channel with the specified {@code channelId}.
     *
     * @param	channelId a channel ID
     * @param	message a channel message
     */
    void channelMessage(BigInteger channelId, ByteBuffer message);

    /**
     * Notifies the associated client that it has successfully logged out.
     */
    void logoutSuccess();
}
