/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.channel;

import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for communicating between peer channel service
 * implementation.
 */
public interface ChannelServer extends Remote {

    /**
     * Notifies this server that the node with the specified {@code
     * nodeId} has one or more sessions that have joined the channel
     * with the specified {@code channelId}.
     *
     * @param	channelId a channel ID
     * @param	nodeId a nodeId
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void join(byte[] channelId, long nodeId)
	throws IOException;

    /**
     * Notifies this server that the node with the specified {@code
     * nodeId} has no more sessions joined to the channel with the
     * specified {@code channelId}.
     *
     * @param	channelId a channel ID
     * @param	nodeId a nodeId
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void leave(byte[] channelId, long nodeId)
	throws IOException;

    /**
     * Notifies this server that all sessions have left the channel
     * with the specified {@code channelId}, and the the client
     * sessions with the specified {@code sessionId}s need to be sent
     * a 'CHANNEL_LEAVE' protocol message.  The sessions with the
     * specified {@code sessionId}s are those sessions that were
     * members of the specified channel that are connected to this
     * server's node.
     *
     * @param	channelId a channelId
     * @param	sessionIds an array of IDs of client sessions that were
     * 		members of the channel and are connected to this server's
     *		node
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void leaveAll(byte[] channelId, byte[][] sessionIds)
	throws IOException;

    /**
     * For each client session recipient (specified by its
     * corresponding client session ID in the {@code recipients} array
     * of ID byte arrays), sends the specified {@code message} to the
     * recipient iff: the recipient is a member of the channel and is
     * connected to the local node.
     *
     * @param	channelId a channel ID
     * @param	recipients a byte array containing a number of
     *		client session ID byte array (an emtpy array means
     *		send to all local sessions on the channel)
     * @param	message a channel message
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void send(byte[] channelId, byte[][] recipients, byte[] message)
	throws IOException;

    /**
     * Notifies this server that the channel with the specified {@code
     * channelId} was closed by the node with the specified {@code
     * nodeId}.
     *
     * @param	channelId a channel ID
     * @param	nodeId a nodeId
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void close(byte[] channelId, long nodeId)
	throws IOException;
}
