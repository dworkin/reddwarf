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

import com.sun.sgs.app.Delivery;
import com.sun.sgs.service.Node;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.HashSet;
import java.util.Set;

/**
 * A protocol for sending session messages and channel messages to
 * a client.
 *
 * <p>Note: If a protocol specification requires that a login
 * acknowledgment be delivered to a client before any other protocol
 * messages, the protocol must implement this requirement.  It is possible
 * that a caller may request that other messages be sent before a login
 * acknowledgment, and if the protocol requires, these messages should be
 * enqueued until the login acknowledgment has been sent to the client.
 *
 * <p>TBD: should these methods throw IOException?
 */
public interface SessionProtocol extends Channel {

    /**
     * Reasons why a server disconnects a session.
     */
    enum DisconnectReason {
	/** The application explicitly terminates a session. */ 
	TERMINATION,
	/** The server preempts a session because of a duplicate login. */
	PREEMPTION
    };

    /**
     * Returns a set containing the delivery requirements supported by
     * this protocol.  The returned set is serializable.
     *
     * @return	a set containing the supported delivery requirements
     */
    Set<Delivery> supportedDeliveries();
    
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
     * @param	channelId the channel's ID
     * @param	delivery the channel's delivery requirement
     *
     * @throws	DeliveryNotSupportedException if the specified {@code
     *		delivery} requirement is not supported by this protocol
     */
    void channelJoin(String name, BigInteger channelId, Delivery delivery);

    /**
     * Notifies the associated client that it is no longer a member of
     * the channel with the specified {@code channelId}.
     *
     * @param	channelId a channel ID
     *
     */
    void channelLeave(BigInteger channelId);

    /**
     * Sends the associated client the specified channel {@code
     * message} sent on the channel with the specified {@code channelId}
     * and {@code delivery} requirement.
     *
     * @param	channelId a channel ID
     * @param	message a channel message
     * @param	delivery the channel's delivery requirement
     *
     * @throws	DeliveryNotSupportedException if the specified {@code
     *		delivery} requirement is not supported by this protocol
     */
    void channelMessage(
	BigInteger channelId, ByteBuffer message, Delivery delivery);


    /**
     * Disconnects the associated session for the specified {@code reason}.
     * The protocol may send a message to the associated client indicating
     * the reason for the disconnection, or the protocol may close the
     * connection immediately.  Any underlying connection(s) should be
     * closed in a timely fashion.
     
     * @param	reason	the reason for disconnection
     */
    void disconnect(DisconnectReason reason);
}
