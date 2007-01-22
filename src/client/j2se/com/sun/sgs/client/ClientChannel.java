package com.sun.sgs.client;

import java.io.IOException;
import java.util.Set;

/**
 * Represents a client's view of a channel.  A channel is a
 * communication group, consisting of multiple clients and the server.
 * <p>
 * The server is solely responsible for creating channels and
 * adding and removing clients from channels.  If desired, a client
 * can request that a channel be created by sending an
 * application-specific message to the server (using its {@link
 * ServerSession}).
 * <p>
 * When the server adds a client session to a channel, the client's
 * {@link ServerSessionListener}'s {@link
 * ServerSessionListener#joinedChannel joinedChannel} method is
 * invoked with that client channel, returning the client's
 * <code>ClientChannelListener</code> for the channel.  A
 * <code>ClientChannelListener</code> for a client channel is notified
 * as follows:
 * <ul>
 * <li>When a message is received on a client channel, the listener's
 * {@link ClientChannelListener#receivedMessage receivedMessage}
 * method is invoked with the channel, the sender's session identifier,
 * and the message.  A <code>null</code> sender indicates that the
 * message was sent by the server.  The listener is <i>not</i>
 * notified of messages that its client sends on its associated
 * channel.</li>
 *
 * <li> When the associated client leaves a channel, the listener's
 * {@link ClientChannelListener#leftChannel leftChannel} method is
 * invoked with the channel.  Once a client has been removed
 * from a channel, that client can no longer send messages on that
 * channel.</li>
 * </ul>
 */
public interface ClientChannel {

    /**
     * Returns the name of this channel.  A channel's name is set when
     * it is created by the server-side application.
     *
     * @return the name of this channel
     */
    String getName();

    /**
     * Sends the given {@code message} to all channel members.  If this
     * channel has no members other than the sender, then no action is taken.
     * <p>
     * The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param message the data to send
     *
     * @throws IllegalStateException if the sender is not a member of
     *         this channel
     * @throws IOException if a synchronous IO problem occurs
     */
    void send(byte[] message) throws IOException;

    /**
     * Sends the given {@code message} to the {@code recipient} on this
     * channel.  If the {@code recipient} is not a member of this channel,
     * then no action is taken.
     * <p>
     * The specified byte array must not be modified after invoking
     * this method; if the byte array is modified, then this method
     * may have unpredictable results.
     *
     * @param recipient the channel member that should receive the message
     * @param message the data to send
     *
     * @throws IllegalStateException if the sender is not a member of
     *         this channel
     * @throws IOException if a synchronous IO problem occurs
     */
    void send(SessionId recipient, byte[] message) throws IOException;

    /**
     * Sends the given {@code message} data to the specified
     * {@code recipients} on this channel. Any {@code recipients} that are
     * not members of this channel are ignored.
     * <p>
     * The specified byte array must not be modified after invoking this
     * method; if the byte array is modified, then this method may have
     * unpredictable results.
     * 
     * @param recipients the subset of channel members that should receive
     *        the message
     * @param message the data to send
     *
     * @throws IllegalStateException if the sender is not a member of this
     *         channel
     * @throws IOException if a synchronous IO problem occurs
     */
    void send(Set<SessionId> recipients, byte[] message) throws IOException;

}
