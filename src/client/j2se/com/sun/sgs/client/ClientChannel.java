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
 * ServerSession}).  When the server adds a client session to a
 * channel, the client's {@link ServerSessionListener} is subsequently
 * notified by having its {@link ServerSessionListener#joinedChannel
 * joinedChannel} method invoked with the client channel.
 *
 * <p>When a client is joined to a <code>ClientChannel</code>, that
 * client should register a {@link ClientChannelListener} with that
 * channel (via the channel's {@link #setListener setListener}
 * method)) so that it can be notified when messages are received on
 * the channel ({@link ClientChannelListener#receivedMessage
 * receivedMessage}), or can be notified if the client has been
 * removed from the channel ({@link ClientChannelListener#leftChannel
 * leftChannel}).  Once a client has been removed from a channel, that
 * client can no longer send messages on that channel.
 *
 * <p>TBD: getDeliveryRequirement method?
 */
public interface ClientChannel {

    /**
     * Returns the name of this channel.
     *
     * @return the name of this channel
     */
    String getName();
    
    /**
     * Sets the listener for messages sent on this channel, replacing
     * the previous listener set.
     *
     * <p>When a message is received on this channel, the specified
     * listener's {@link ClientChannelListener#receivedMessage
     * receivedMessage} method is invoked with this channel, the
     * sender and the message.
     *
     * <p>DO WE WANT THIS?  The specified listener is not invoked for
     * messages that a client sends on this channel via one of the
     * channel's <code>send</code> methods.
     *
     * @param listener a channel listener
     *
     * @throws IllegalStateException if the associated client has been
     * removed from this channel
     */
    void setListener(ClientChannelListener listener);
    
    /**
     * Sends the specified message to all clients joined to this
     * channel.  If no clients are joined to this channel, then no
     * action is taken.
     *
     * @param message a message
     *
     * @throws IllegalStateException if the associated client has been
     * removed from this channel
     */
    void send(byte[] message);

    /**
     * Sends the specified message to the specified recipient.  If the
     * specified recipient is not joined to this channel, then no
     * action is taken.
     *
     * @param recipient a recipient
     * @param message a message
     *
     * @throws IllegalStateException if the associated client has been
     * removed from this channel
     */
    void send(ClientAddress recipient, byte[] message);

    /**
     * Sends the specified message to the recipients contained in the
     * specified collection.  Any specified clients that are not
     * currently joined to the channel are ignored.
     *
     * @param recipients a collection of client recipients
     * @param message a message
     *
     * @throws IllegalStateException if the associated client has been
     * removed from this channel
     */
    void send(Collection<ClientAddress> recipients, byte[] message);
}
