package com.sun.sgs.client;

/**
 * Listener for events relating to a {@link ClientChannel}.
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
 * message was sent by the server.   The listener is <i>not</i>
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
public interface ClientChannelListener {

    /**
     * Notifies this listener that the specified message, sent by the
     * specified sender, was received on the specified channel.  If
     * the specified sender is <code>null</code>, then the specified
     * message was sent by the server.
     *
     * @param channel a client channel
     * @param sender sender's session identifier, or <code>null</code>
     * @param message a byte array containing a message.
     */
    void receivedMessage(ClientChannel channel,
			 SessionId sender,
			 byte[] message);

    /**
     * Notifies this listener that the associated client was removed
     * from the specified channel.  The associated client can no
     * longer send messages on the specified channel.
     *
     * @param channel a client chanel
     */
    void leftChannel(ClientChannel channel);
}
