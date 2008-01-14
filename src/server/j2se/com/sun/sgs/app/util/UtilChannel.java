/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app.util;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionException;

/**
 * Interface representing a communication group, a
 * {@code UtilChannel}, consisting of multiple client sessions and
 * the server.  Classes that implement {@code UtilChannel} must
 * also implement {@link Serializable}.
 *
 * <p>A channel is created by invoking the {@link
 * UtilChannelManager#getChannel(String) UtilChannelManager.createChannel}
 * method with a name for the channel, a {@link UtilChannelListener}, and
 * a {@link Delivery} requirement.  A {@link ClientSession} can
 * be added or removed from a channel using that
 * {@code UtilChannel}'s {@link #join join} and {@link #leave leave}
 * methods respectively.  All client sessions can be removed from a
 * channel by invoking {@link #leaveAll leaveAll} on the channel.
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
 * <p>If a channel is created with a {@link UtilChannelListener}, then
 * when any client session sends a message on that channel, that
 * listener's {@link UtilChannelListener#receivedMessage
 * receivedMessage} method is invoked with that channel, the client
 * session, and the message.
 *
 * <p>If the server needs to receive notification of messages sent by
 * an individual client session on a channel, the application can
 * specify a non-{@code null} {@link UtilChannelListener} when
 * invoking the {@link #join join} method to add a client session to a
 * channel.
 *
 * <p> Note that no registered listeners are notified of messages sent
 * by the server on the channel; listeners are only notified (as
 * described above) when client sessions send messages on the channel.
 *
 * <p>A client session joined to one or more channels may become
 * disconnected due to the client logging out or due to other factors
 * such as forced disconnection or network failure.  If a client
 * session becomes disconnected, then for each channel the session is
 * joined to:
 * <ul>
 * <li> that client session is removed from the channel
 * <li> if an individual {@code UtilChannelListener} is registered
 * for that session, then that listener is unregistered from the
 * channel
 * </ul>
 *
 * <p>When the application is finished using a channel, the
 * application should invoke that channel's {@link #close close}
 * method so that its resources can be reclaimed and its named binding
 * removed.  Once a channel is closed, any of its side-effecting
 * operations will throw {@link IllegalStateException} if invoked.
 *
 * @see UtilChannelManager#createChannel UtilChannelManager.createChannel
 * @see UtilChannelManager#getChannel UtilChannelManager.getChannel
 */
public class UtilChannel
    implements ManagedObject, ManagedObjectRemoval, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1L;

    /** A reference to the underlying {@code Channel}. */
    private final ManagedReference channelRef;

    /**
     * TODO doc
     */
    protected UtilChannel() {
        channelRef = null; // TODO
    }

    // implement augmented channel functionality

    /**
     * Returns the name bound to this channel.
     *
     * @return the name bound to this channel
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public String getName() {
        // TODO
        return null;
    }

    /**
     * Returns the delivery requirement of this channel.
     *
     * @return the delivery requirement
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public Delivery getDeliveryRequirement() {
        // TODO
        return null;
    }

    /**
     * Adds a client session to this channel.  If the specified
     * session is already joined to this channel, then no action is
     * taken.
     *
     * <p>If the specified {@code listener} is
     * non-{@code null}, then when the specified client session
     * sends a message on this channel, the specified listener's {@link
     * UtilChannelListener#receivedMessage
     * receivedMessage} method is invoked with this channel, the
     * session, and the message.  The specified listener is not
     * invoked for messages that the server sends on this channel via
     * one of the channel's {@code send} methods.  If the specified
     * listener is non-{@code null} then it must also be serializable.
     *
     * <p>Note: This operation has no effect on notifications to the
     * channel listener specified when this channel was created.
     *
     * @param session a session
     * @param listener a channel listener, or {@code null}
     *
     * @throws IllegalArgumentException if {@code listener} is
     * non-{@code null} and is not {@code Serializable}
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public void join(ClientSession session, UtilChannelListener listener) {
        // TODO
    }

    /**
     * Removes a client session from this channel.  If the specified
     * session is not joined to this channel, then no action is taken.
     *
     * <p>If an individual {@link UtilChannelListener} is registered for
     * the specified session, that listener is unregistered.
     *
     * @param session a session
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public void leave(ClientSession session) {
        // TODO
    }

    /**
     * Removes all client sessions from this channel.
     *
     * <p>Each {@link UtilChannelListener} registered for an individual
     * client session on this channel is unregistered.
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public void leaveAll() {
        // TODO
    }

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
    public boolean hasSessions() {
        // TODO
        return false;
    }

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
    public Iterator<ClientSession> getSessions() {
        // TODO
        return null;
    }

    /**
     * Sends the message contained in the specified buffer to all
     * client sessions joined to this channel.  If no sessions are
     * joined to this channel, then no action is taken.
     *
     * <p>The specified buffer must not be modified after invoking
     * this method; otherwise this method may have unpredictable results.
     *
     * @param message a message
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public void send(byte[] message) {
        // TODO
    }

    public void send(Set<ClientSession> recipients, byte[] message) {
        // TODO
    }

    /**
     * Closes this channel and removes its named binding.  If this
     * channel is already closed, then no action is taken.
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    public void close() {
        // TODO
    }

    // dispatch hook

    protected void dispatch(ByteBuffer message) throws IOException {
        // TODO        
    }

    // implement ManagedObjectRemoval

    /**
     * {@inheritDoc}
     */
    public void removingObject() {
        // TODO
        
    }

}
