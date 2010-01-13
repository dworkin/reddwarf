/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.Delivery;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.Remote;

/**
 * A remote interface for communicating channel events and other
 * channel-related requests between peer channel services. <p>
 *
 * Each channel event (join, leave, send, close) is assigned a
 * <i>timestamp</i> when the event is added to the channel's event queue.
 * An event queue's current timestamp records the timestamp of the latest
 * send event that the queue has started to process. The initial event
 * timestamp is <code>1</code>.  A send event increments the next
 * timestamp, so all future join and leave events will have a later
 * timestamp.  Because only a send event increments the next timestamp,
 * join, leave, and send requests may share the same timestamp. If a send
 * event at timestamp <code><i>t</i></code> is followed directly by a
 * series of join and leave events, the join and leave events will all
 * have the later timestamp <code><i>t+1</i></code>.  The next send event
 * for the channel will also have timestamp <code><i>t+1</i></code>.<p>
 *
 * If a session becomes a channel member at a given timestamp
 * <code><i>t</i></code>, then it should receive all channel messages
 * with a timestamp greater than or equal to <code><i>t</i></code>.  If a
 * session leaves a channel at a given timestamp <code><i>t</i></code>,
 * the session should receive channel messages up to timestamp
 * <code><i>t-1</i></code>, but should not receive channel messages
 * greater than or equal to timestamp <code><i>t</i></code>.<p>
 *
 * A channel server that receives a channel event notification (for
 * delivery to a client session) uses the event's timestamp to determine
 * whether the event needs to be delivered to a local session.  The
 * timestamp is used to determine duplicate events (in the case of
 * coordinator recovery), and if a client session relocates, the
 * session's current timestamp for a channel can be used to determine
 * whether the session missed any channel messages while relocating.
 */
public interface ChannelServer extends Remote {

    /** Membership status. */
    public enum MembershipStatus {
	/** Member. */
	MEMBER,
	/** Non-member. */
	NON_MEMBER,
	/** Unknown (because session is non-local). */
	UNKNOWN;
    };

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
     * If the session with the specified {@code sessionRefId} is connected to
     * the local node, returns {@link MembershipStatus#MEMBER} if the session
     * is a member of the channel with the specified {@code channelRefId} and
     * returns {@link MembershipStatus#NON_MEMBER} if the session is not a
     * member of the channel.  If the session is not connected to the local
     * node, then this method returns {@link MembershipStatus#UNKNOWN}.
     *
     * @param	channelRefId a channel ID
     * @param	sessionRefId a session ID
     * @return	the membership status of the session with the
     *		specified {@code sessionRefId} for the channel with
     *		the specified {@code channelRefId}
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    MembershipStatus isMember(BigInteger channelRefId, BigInteger sessionRefId)
	throws IOException;
    
    /**
     * Notifies this server that the locally-connected session with
     * the specified {@code sessionRefId} has joined the channel with
     * the specified {@code name} and {@code channelRefId}.
     *
     * @param	name a channel name
     * @param	channelRefId a channel ID
     * @param	deliveryOrdinal the channel's delivery requirement, as a {@link
     *		Delivery} ordinal
     * @param	timestamp the timestamp of the last channel message sent
     * @param	sessionRefId a session ID
     * @return	{@code true} if the join succeeded (either was delivered or
     *		enqueued for a relocating session), and {@code false} if
     *		the session is not locally connected and is not known to be
     *		relocating 
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean join(String name, BigInteger channelRefId, byte deliveryOrdinal,
		 long timestamp, BigInteger sessionRefId)
	throws IOException;

    /**
     * Notifies this server that the locally-connected session with
     * the specified {@code sessionRefId} has left the channel with the
     * specified {@code channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @param	timestamp the timestamp of the last channel message sent
     * @param	sessionRefId a session ID
     * @return	{@code true} if the leave succeeded (either was delivered or
     *		enqueued for a relocating session), and {@code false} if
     *		the session is not locally connected and is not known to be
     *		relocating 
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean leave(BigInteger channelRefId, long timestamp,
		  BigInteger sessionRefId)
	throws IOException;

    /**
     * Returns an array containing the client session ID of each client
     * session on this node that is a member of the channel with the
     * specified {@code channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @return	an array of members' session IDs
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    BigInteger[] getSessions(BigInteger channelRefId)
	throws IOException;
    
    /**
     * Sends the specified message to all locally-connected sessions
     * that are members of the channel with the specified {@code
     * channelRefId}.
     *
     * @param	channelRefId a channel ID
     * @param	message a channel message
     * @param	timestamp the message's timestamp
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void send(BigInteger channelRefId, byte[] message, long timestamp)
	throws IOException;

    /**
     * Notifies this server that the client session with the specified
     * {@code sessionRefId} is relocating from the node (specified by
     * {@code oldNodeId}) to the new node (i.e., the local node) and that
     * the session's channel memberships should be updated accordingly.
     * The {@code channelRefIds} array contains the channel ID of each
     * channel that the client session belongs to.  The {@code
     * deliveryOrdinals} array contains the delivery ordinal of each
     * corresponding channel. The {@code msgTimestamps} array contains the
     * timestamp of the latest message received by the specified session
     * (at the point that relocation started) for each corresponding
     * channel.<p>
     *
     * This server must update its local channel membership cache for the
     * specified session and add persistent membership information to
     * indicate that the specified session on the local node is now joined
     * to each channel.  When the cache and persistent membership
     * information is updated, the {@link #relocateChannelMembershipsCompleted
     * relocateChannelMembershipsCompleted} method should be invoked on the
     * old node's {@code ChannelServer} with the specified {@code
     * sessionRefId} and the new node's ID.
     *
     * @param	sessionRefId the ID of a client session relocating to the
     *		local node
     * @param	oldNodeId the ID of the node the session is relocating from
     * @param	channelRefIds an array that contains the channel ID of each
     *		channel that the client session is a member of
     * @param	deliveryOrdinals an array that contains the delivery ordinal
     *		of each channel that the client session is a member of
     * @param	msgTimestamps an array that contains the message timestamp
     *		of each channel that the client session is a member of
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void relocateChannelMemberships(BigInteger sessionRefId, long oldNodeId,
				    BigInteger[] channelRefIds,
				    byte[] deliveryOrdinals,
				    long[] msgTimestamps)
	throws IOException;

    /**
     * Notifies this server that the channel server on the node specified
     * by {@code newNodeId} has completed updating the channel memberships
     * for the client session with the specified {@code sessionRefId} in
     * preparation for the session's relocation to the new node.  This
     * method is invoked when the work associated with a previous
     * invocation to {@link #relocateChannelMemberships
     * relocateChannelMemberships} on the new node's channel server is
     * complete. This channel server should clean up any remaining
     * persistent channel membership information for the session on the old
     * node (i.e., the local node).
     *
     * @param	sessionRefId the ID of a relocating client session
     * @param	newNodeId ID of the node the session is relocating to
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void relocateChannelMembershipsCompleted(
	BigInteger sessionRefId, long newNodeId)
	throws IOException;
			 
    /**
     * Notifies this server that the channel with the specified {@code
     * channelRefId} is closed.
     *
     * @param	channelRefId a channel ID
     * @param	timestamp the timestamp of the last channel message sent
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void close(BigInteger channelRefId, long timestamp) throws IOException;
}
