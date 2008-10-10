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

/**
 * A channel for sending protocol messages to a client.  A {@code
 * ProtocolMessageChannel} should have a constructor that takes the
 * following arguments:
 * <ul>
 * <li>{@link java.util.Properties}</li>
 * <li>{@link com.sun.sgs.kernel.ComponentRegistry}</li>
 * <li>{@link com.sun.sgs.nio.channels.AsynchronousByteChannel}</li>
 * <li>{@link ProtocolMessageHandler}
 * </ul>
 *
 * <p>TBD: should reconnection be handled a this layer or transparently by
 * the transport layer?   Perhaps the {@code AsynchronousByteChannel}
 * managed by the transport layer could handle the reconnection under the
 * covers.
 */
public interface ProtocolMessageChannel {

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
     * successful.
     */
    void loginSuccess();

    /**
     * Notifies the associated client that the previous login attempt
     * was unsuccessful for the specified {@code reason}.
     *
     * @param	reason a reason why the login was unsuccessful
     */
    void loginFailure(String reason);

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
     * Closes this protocol message channel which should disconnect the
     * associated client.
     */
    void close();
}
