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

import com.sun.sgs.app.Delivery;
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
     * For each client session recipient (specified by its
     * corresponding client session ID in the {@code recipients} array
     * of ID byte arrays), sends the specified {@code protocolMessage}
     * to the recipient if the recipient is connected to the local
     * node.
     *
     * @param	channelId a channel ID
     * @param	recipients an array containing client session ID
     *		byte arrays
     * @param	message a protocol message
     * @param	delivery the delivery guarantee
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void send(byte[] channelId,
	      byte[][] recipients,
	      byte[] message,
	      Delivery delivery)
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
