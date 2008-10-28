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
 * A protocol for sending channel-related messages to a client.  A {@code
 * ChannelProtocol} is created using the {@link
 * ChannelProtocolFactory#newChannelProtocol} method.
 */
public interface ChannelProtocol extends Channel {
    
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

}
