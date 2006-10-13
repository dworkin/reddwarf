package com.sun.sgs.client;

/**
 * Listener for events relating to a {@link ClientChannel}.
 *
 * <p>When the server adds a client session to a channel, the client's
 * {@link ServerSessionListener} is subsequently notified by having
 * its {@link ServerSessionListener#joinedChannel joinedChannel}
 * method invoked with the client channel.
 *
 * <p>When a client is joined to a <code>ClientChannel</code>, the
 * client should register a <code>ClientChannelListener</code> with
 * that channel (via the channel's {@link ClientChannel#setListener
 * setListener} method)) so that it can be notified when messages are
 * received on the channel ({@link #receivedMessage receivedMessage}),
 * or can be notified if the client has been removed from the channel
 * ({@link #leftChannel leftChannel}).  Once a client has been removed
 * from a channel, that client can no longer send messages on that
 * channel.
 *
 */
public interface ClientChannelListener {

    /**
     * Notifies this listener that the specified message, sent by the
     * specified client, was received on the specified channel.
     *
     * @param channel a client chanel
     * @param client a client
     * @param message a messagse
     */
    void receivedMessage(ClientChannel channel,
			 ClientAddress client,
			 byte[] message);

    /**
     * Notifies this listener that the associated client was removed
     * from the specified channel.
     *
     * @param channel a client chanel
     */
    void leftChannel(ClientChannel channel);
}
