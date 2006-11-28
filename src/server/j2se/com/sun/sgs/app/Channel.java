package com.sun.sgs.app;

import java.util.Collection;

/**
 * Interface representing a communication group, a
 * <code>Channel</code>, consisting of multiple client sessions and
 * the server.
 *
 * <p>A channel is created by invoking the {@link
 * ChannelManager#getChannel(String) ChannelManager.createChannel}
 * method with a name for the channel, a {@link ChannelListener}, and
 * a {@link Delivery} requirement.  A {@link ClientSession} can
 * be added or removed from a channel using that
 * <code>Channel</code>'s {@link #join join} and {@link #leave leave}
 * methods respectively.  All client sessions can be removed from a
 * channel by invoking {@link #leaveAll leaveAll} on the channel.
 *
 * <p>The server can send a message to any or all client sessions
 * joined to a channel by using one of the channel's <code>send</code>
 * methods.  Note that the methods of this interface are invoked
 * within the context of an executing {@link Task}; as a result, a
 * message sent to client sessions using one of the channel's
 * <code>send</code> methods will not be sent until after its
 * corresponding task completes.  Messages sent on a channel will be
 * delivered according to that channel's {@link Delivery} requirement,
 * specified at channel creation time.
 *
 * <p>If a channel is created with a {@link ChannelListener}, then
 * when any client session sends a message on that channel, that
 * listener's {@link
 * ChannelListener#receivedMessage(Channel,ClientSession,byte[])
 * receivedMessage} method is invoked with that channel, the client
 * session, and the message.
 *
 * <p>If the server needs to receive notification of messages sent by
 * an individual client session on a channel, the application can
 * specify a non-<code>null</code> {@link ChannelListener} when
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
 * <li> if an individual <code>ChannelListener</code> is registered
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
 * @see ChannelManager#createChannel ChannelManager.createChannel
 * @see ChannelManager#getChannel ChannelManager.getChannel
 */
public interface Channel {

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
     * <p>If the specified <code>listener</code> is
     * non-<code>null</code>, then when the specified client session
     * sends a message on this channel, the specified listener's {@link
     * ChannelListener#receivedMessage(Channel,ClientSession,byte[])
     * receivedMessage} method is invoked with this channel, the
     * session, and the message.  The specified listener is not
     * invoked for messages that the server sends on this channel via
     * one of the channel's <code>send</code> methods.  If the specified
     * listener is non-<code>null</code> then it must also be serializable.
     *
     * <p>Note: This operation has no effect on notifications to the
     * channel listener specified when this channel was created.
     *
     * @param session a session
     * @param listener a channel listener, or <code>null</code>
     *
     * @throws IllegalArgumentException if <code>listener</code> is
     * non-<code>null</code> and is not <code>Serializable</code>
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void join(ClientSession session, ChannelListener listener);

    /**
     * Removes a client session from this channel.  If the specified
     * session is not joined to this channel, then no action is taken.
     *
     * <p>If an individual {@link ChannelListener} is registered for
     * the specified session, that listener is unregistered.
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
     * <p>Each {@link ChannelListener} registered for an individual
     * client session on this channel is unregistered.
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void leaveAll();

    /**
     * Returns <code>true</code> if this channel has client sessions
     * joined to it, otherwise returns <code>false</code>.
     *
     * @return <code>true</code> if this channel has sessions joined
     * to it, otherwise returns <code>false</code>.
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    boolean hasSessions();

    /**
     * Returns a collection containing the client sessions joined to
     * this channel.
     *
     * <p>Note: This operation may be expensive, so it should be used
     * judiciously.
     * 
     * @return a collection containing the sessions joined to this channel
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    Collection<ClientSession> getSessions();

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
     * Sends the message contained in the specified byte array to the
     * specified recipient session.  If the specified client session
     * is not joined to this channel, then no action is taken.
     *
     * <p>The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param recipient a recipient
     * @param message a message
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void send(ClientSession recipient, byte[] message);

    /**
     * Sends a message contained in the specified byte array to the
     * client sessions in the specified collection.  Any specified
     * recipient sessions that are not currently joined to the channel
     * are ignored.
     *
     * <p>The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param recipients a collection of recipient sessions
     * @param message a message
     *
     * @throws IllegalStateException if this channel is closed
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void send(Collection<ClientSession> recipients, byte[] message);

    /**
     * Closes this channel and removes its named binding.  If this
     * channel is already closed, then no action is taken.
     *
     * @throws TransactionException if the operation failed because of
     * a problem with the current transaction
     */
    void close();
}
