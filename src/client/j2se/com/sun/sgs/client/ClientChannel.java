package com.sun.sgs.client;

import java.util.Collection;

/**
 * Represents a client's view of a channel.  A channel is a
 * communication group, consisting of multiple clients and the server.
 *
 * <p>The server is solely responsible for creating channels and
 * adding and removing clients from channels.  If desired, a client
 * can request that a channel be created by sending an
 * application-specific message to the server (using its {@link
 * ServerSession}).
 *
 * <p>When the server adds a client session to a channel, the client's
 * {@link ServerSessionListener}'s {@link
 * ServerSessionListener#joinedChannel joinedChannel} method is
 * invoked with that client channel, returning the client's
 * <code>ClientChannelListener</code> for the channel.  A
 * <code>ClientChannelListener</code> for a client channel is notified
 * as follows: <ul>
 *
 * <li>When a message is received on a client channel, the listener's
 * {@link ClientChannelListener#receivedMessage receivedMessage}
 * method is invoked with the channel, the sender's session identifier,
 * and the message.  A <code>null</code> sender indicates that the
 * message was sent by the server.  The listener is <i>not</i>
 * notified of messages that its client sends on its associated
 * channel.
 *
 * <li> When the associated client leaves a channel, the listener's
 * {@link ClientChannelListener#leftChannel leftChannel} method is
 * invoked with the channel.  Once a client has been removed
 * from a channel, that client can no longer send messages on that
 * channel.
 * </ul>
 */
public interface ClientChannel {

    /**
     * Returns the name of this channel, which is the name that the
     * server assigned to the channel.
     *
     * @return the name of this channel
     */
    String getName();
    
    /**
     * Sends the message contained in the specified byte array to all
     * clients joined to this channel.  If no clients are joined to
     * this channel, then no action is taken.
     *
     * <p>The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param message a message
     *
     * @throws IllegalStateException if the associated client has been
     * removed from this channel
     */
    void send(byte[] message);

    /**
     * Sends the message contained in the specified byte array to the
     * specified recipient.  If the specified recipient is not joined
     * to this channel, then no action is taken.
     *
     * <p>The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param recipient the session identifier of a recipient
     * @param message a message
     *
     * @throws IllegalStateException if the associated client has been
     * removed from this channel
     */
    void send(SessionId recipient, byte[] message);

    /**
     * Sends the message contained in the specified byte array to the
     * recipients contained in the specified collection.  Any
     * specified clients that are not currently joined to the channel
     * are ignored.
     *
     * <p>The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param recipients a collection of client recipients
     * @param message a message
     *
     * @throws IllegalStateException if the associated client has been
     * removed from this channel
     */
    void send(Collection<SessionId> recipients, byte[] message);
}
