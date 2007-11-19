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

package com.sun.sgs.app;

import java.io.Serializable;
import java.util.Iterator;

/**
 * Interface representing a communication group, a
 * {@code Channel}, consisting of multiple client sessions and
 * the server.  Classes that implement {@code Channel} must
 * also implement {@link Serializable}.
 *
 * <p>A channel is created by invoking the {@link
 * ChannelManager#getChannel(String) ChannelManager.createChannel}
 * method with a name for the channel and a {@link Delivery}
 * requirement.  A {@link ClientSession} can be added or removed from
 * a channel using that {@code Channel}'s {@link #join join} and
 * {@link #leave leave} methods respectively.  All client sessions can
 * be removed from a channel by invoking {@link #leaveAll leaveAll} on
 * the channel.
 *
 * <p>The server can send a message to any or all client sessions
 * joined to a channel by using one of the channel's {@code send}
 * methods.  Note that the methods of this interface are invoked
 * within the context of an executing {@link Task}; as a result, a
 * message sent to client sessions using one of the channel's
 * {@code send} methods will not be sent until after its
 * corresponding task completes.  Messages sent on a channel will be
 * delivered according to that channel's {@link Delivery} requirement,
 * specified at channel creation time.
 *
 * <p>A client session can send a message to a channel only if it is a
 * member of that channel.  A client session sends a channel message
 * by sending a request to server using a protocol employed by the
 * implementation.  A requested channel message is delivered,
 * according to the channel's delivery requirement, to all requested
 * recipients except for the sending client session.
 *
 * <p>A client session joined to one or more channels may become
 * disconnected due to the client logging out or due to other factors
 * such as forced disconnection or network failure.  If a client
 * session becomes disconnected, then that client session is removed
 * from each channel that it is amember of.
 *
 * <p>When the application is finished using a channel, the
 * application should invoke that channel's {@link #close close}
 * method so that its resources can be reclaimed and its named binding
 * removed.  Once a channel is closed, any of its side-effecting
 * operations will throw {@link IllegalStateException} if invoked.
 *
 * @see ChannelManager#createChannel ChannelManager.createChannel
 * @see ChannelManager#getChannel ChannelManager.getChannel
 */
public interface Channel extends ManagedObject {

    /**
     * Returns the name bound to this channel.
     *
     * @return the name bound to this channel
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    String getName();


    /**
     * Returns the delivery requirement of this channel.
     *
     * @return the delivery requirement
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    Delivery getDeliveryRequirement();

    /**
     * Adds a client session to this channel.  If the specified
     * session is already joined to this channel, then no action is
     * taken.
     *
     * @param session a session
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void join(ClientSession session);

    /**
     * Removes a client session from this channel.  If the specified
     * session is not joined to this channel, then no action is taken.
     *
     * @param session a session
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void leave(ClientSession session);

    /**
     * Removes all client sessions from this channel.
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void leaveAll();

    /**
     * Returns {@code true} if this channel has client sessions
     * joined to it, otherwise returns {@code false}.
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
     * this channel.
     *
     * <p>Note: This operation may be expensive, so it should be used
     * judiciously.
     * 
     * @return an iterator for the client sessions joined to this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    Iterator<ClientSession> getSessions();

    /**
     * Sends the message contained in the specified byte array to all
     * client sessions joined to this channel.  If no sessions are
     * joined to this channel, then no action is taken.
     *
     * <p>The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param message a message
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void send(byte[] message);

    /**
     * Closes this channel and removes its named binding.  If this
     * channel is already closed, then no action is taken.
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void close();
}
