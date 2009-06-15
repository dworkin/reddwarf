/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
import java.math.BigInteger;
import java.rmi.Remote;

/**
 * A remote interface for communicating between peer channel service
 * implementation.
 */
public interface ChannelServer extends Remote {

    /**
     * Notifies this server that it should service the event queue of
     * the channel with the specified {@code channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void serviceEventQueue(BigInteger channelRefId) throws IOException;

    /**
     * Notifies this server that it should reread the channel
     * membership list of the specified {@code channelRefId} for
     * sessions connected to this node before processing any other
     * events on the channel.  {@code refresh} requests are sent
     * when a node performs recovery operations for a channel
     * coordinator failure.  When a channel coordinator fails, a
     * {@code join}, {@code leave}, or other event notification may
     * be lost, so any local channel membership information that is
     * cached may be stale and needs to be reread before processing
     * any more events.
     *
     * @param	name a channel name
     * @param	channelRefId a channel ID
     * @param	delivery the channel's delivery requirement
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void refresh(String name, BigInteger channelRefId, Delivery delivery)
	throws IOException;

    /**
     * Notifies this server that the locally-connected session with
     * the specified {@code sessionRefId} has joined the channel with
     * the specified {@code name} and {@code channelRefId}.
     *
     * @param	name a channel name
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID
     * @param	delivery the channel's delivery requirement
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void join(String name, BigInteger channelRefId, Delivery delivery,
	      BigInteger sessionRefId)
	throws IOException;

    /**
     * Notifies this server that the locally-connected session with
     * the specified {@code sessionRefId} has left the channel with the
     * specified {@code channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void leave(BigInteger channelRefId, BigInteger sessionRefId)
	throws IOException;

    /**
     * Notifies this server that all locally-connected member sessions
     * have left the channel with the specified {@code channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void leaveAll(BigInteger channelRefId) throws IOException;

    /**
     * Sends the specified message to all locally-connected sessions
     * that are members of the channel with the specified {@code
     * channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @param	message a channel message
     * @param	deliveryOrdinal a delivery guarantee (the
     *		ordinal representing the {@link Delivery} enum)
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void send(BigInteger channelRefId, byte[] message, byte deliveryOrdinal)
	throws IOException;

    /**
     * Notifies this server that the client session with the specified
     * {@code sessionRefId} is relocating from the node (specified by
     * {@code oldNodeId}) to the mew node (i.e., the local node) and that
     * the session's channel memberships should be updated accordingly.
     * The {@code channelRefIds} array contains the channel ID of each
     * channel that the client session belongs to.  This server must update
     * its local channel membership cache for the specified session and add
     * persistent membership information to indicate that the specified
     * session on the local node is now joined to each channel.  When the
     * cache and persistent membership information is updated, the {@link
     * #channelMembershipsUpdated channelMembershipsUpdated} method should
     * be invoked on the old node's {@code ChannelServer} with the
     * specified {@code sessionRefId} and the new node's ID.
     *
     * @param	sessionRefId the ID of a client session relocating to the
     *		local node
     * @param	oldNodeId ID of the node the session is relocating from
     * @param	channelRefIds an array that contains the channel ID of each
     *		channel that the client session is a member of
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void relocateChannelMemberships(BigInteger sessionRefId, long oldNodeId,
				    BigInteger[] channelRefIds)
	throws IOException;

    /**
     * Notifies this server that the channel server on the node specified
     * by {@code newNodeId} has completed updating the channel memberships
     * for the client session with the specified {@code sessionRefId} in
     * preparation for the session's relocation to the new node.  This
     * method is invoked when the work associated with a previous
     * invocation to {@link #relocateSession relocateSession} on the new
     * node's channel server is complete. This channel server should clean
     * up any remaining persistent channel membership information for the
     * session on the old node (i.e., the local node).
     * any
     *
     * @param	sessionRefId the ID of a relocating client session
     * @param	newNodeId ID of the node the session is relocating to
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void channelMembershipsUpdated(BigInteger sessionRefId, long newNodeId)
	throws IOException;
			 
    /**
     * Notifies this server that the channel with the specified {@code
     * channelRefId} is closed.
     *
     * @param	channelRefId a channel ID
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void close(BigInteger channelRefId) throws IOException;
}
