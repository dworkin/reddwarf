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

package com.sun.sgs.app;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;

/**
 * Interface representing a communication group, a {@code Channel},
 * consisting of multiple client sessions.  Classes that implement
 * {@code Channel} must also implement {@link Serializable}.
 *
 * <p>A channel is created by invoking the {@link
 * ChannelManager#createChannel ChannelManager.createChannel} method
 * with a {@link Delivery} requirement.  A {@link ClientSession} can
 * be added or removed from a channel using that {@code Channel}'s
 * {@link #join(ClientSession) join} and
 * {@link #leave(ClientSession) leave} methods respectively.
 * All client sessions can be removed from a channel by invoking
 * {@link #leaveAll leaveAll} on the channel.
 *
 * <p>The server can send a message to all client sessions joined to a
 * channel by using the {@link #send send} method.  Note that the
 * methods of this interface are invoked within the context of an
 * executing {@link Task}; as a result, a message sent to client
 * sessions using the {@code send} method will not be sent until after
 * its corresponding task completes.  Messages sent on a channel will
 * be delivered according to that channel's {@link Delivery}
 * requirement, specified at channel creation time.
 *
 * <p>A client session joined to one or more channels may become
 * disconnected due to the client logging out or due to other factors
 * such as forced disconnection or network failure.  If a client
 * session becomes disconnected, then that client session is removed
 * from each channel that it is a member of.
 *
 * <p>When the application is finished using a channel, the
 * application should remove the channel from the data manager, which
 * closes the channel and releases all resources associated with the
 * channel. 
 *
 * @see ChannelManager#createChannel ChannelManager.createChannel
 */
public interface Channel extends ManagedObject {

    /**
     * Returns the name bound to this channel.
     *
     * @return the name bound to this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    String getName();
    
    /**
     * Returns the delivery requirement of this channel.
     *
     * @return the delivery requirement
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     *	       a problem with the current transaction
     */
    Delivery getDeliveryRequirement();

    /**
     * Returns {@code true} if this channel has client sessions
     * joined to it, otherwise returns {@code false}.
     *
     * <p>The returned result may not reflect changes to the membership
     * that occurred in the current transaction.  Such membership changes
     * may be handled asynchronously, after the task making the changes
     * completes.
     *
     * @return {@code true} if this channel has sessions joined
     * to it, otherwise returns {@code false}
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    boolean hasSessions();

    /**
     * Returns an iterator for the client sessions joined to
     * this channel.  The returned iterator may only be used in the task
     * that this method was invoked from.
     *
     * <p>The returned iterator may not reflect changes to the membership
     * that occurred in the current transaction.  Such membership changes
     * may be handled asynchronously, after the task makiing the changes
     * completes.  Therefore, the iterator <i>may not</i> include sessions
     * that have been recently joined to the channel, or <i>may</i> include
     * sessions that have recently left the channel (by being explicitly
     * removed from the channel, or by being disconnected).
     *
     * <p>Note: This operation may be expensive, so it should be used
     * judiciously.
     * 
     * @return an iterator for the sessions joined to this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    Iterator<ClientSession> getSessions();

    /**
     * Adds a client session to this channel.  If the specified
     * session is already joined to this channel, then no action is
     * taken.
     *
     * @param session a client session
     *
     * @return this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws ResourceUnavailableException if there are not enough resources
     *	       to join the channel
     * @throws TransactionException if the operation failed because of
     *	       a problem with the current transaction
     */
    Channel join(ClientSession session);

    /**
     * Adds the specified client sessions to this channel.
     *
     * @param sessions a set of client sessions
     *
     * @return this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws ResourceUnavailableException if there are not enough resources
     *	       to join the channel
     * @throws TransactionException if the operation failed because of
     *	       a problem with the current transaction
     */
    Channel join(Set<ClientSession> sessions);
    
    /**
     * Removes a client session from this channel.  If the specified
     * session is not joined to this channel, then no action is taken.
     *
     * @param session a client session
     *
     * @return this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     *	       a problem with the current transaction
     */
    Channel leave(ClientSession session);

    /**
     * Removes the specified client sessions from this channel, If a
     * session in the specified set is not joined to this channel,
     * then no action for that session is taken.
     *
     * @param sessions a set of client sessions
     *
     * @return this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     *	       a problem with the current transaction
     */
    Channel leave(Set<ClientSession> sessions);
    
    /**
     * Removes all client sessions from this channel.
     *
     * @return this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     *	       a problem with the current transaction
     */
    Channel leaveAll();

    /**
     * Sends the message contained in the specified buffer to all
     * client sessions joined to this channel.  If no sessions are
     * joined to this channel, then no action is taken. The message starts
     * at the buffer's current position and ends at the buffer's limit.
     * The buffer's position is not modified by this operation.
     *
     * <p>If the specified {@code sender} is non-{@code null} and that
     * sender is not a member of this channel when the message is processed
     * to be sent, then the message will not be forwarded to the channel
     * for delivery.
     * 
     * <p>The {@code ByteBuffer} may be reused immediately after this method
     * returns.  Changes made to the buffer after this method returns will
     * have no effect on the message sent to the channel by this invocation.
     *
     * @param	sender the sending client session, or {@code null}
     * @param	message a message
     *
     * @return	this channel
     *
     * @throws	IllegalStateException if this channel is closed
     * @throws	MessageRejectedException if there are not enough resources
     *		to send the specified message
     * @throws	TransactionException if the operation failed because of
     *		a problem with the current transaction
     */
    Channel send(ClientSession sender, ByteBuffer message);

}
