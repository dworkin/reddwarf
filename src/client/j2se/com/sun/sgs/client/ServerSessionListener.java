package com.sun.sgs.client;

import java.nio.ByteBuffer;

/**
 * A client's listener for handling messages sent from server to
 * client and for handling channel-joined events.
 *
 * <p>A <code>ServerSessionListener</code> for a client (specified as
 * part of the login procedure...) is notified in the following cases:
 * the associated client is joined to a
 * channel ({@link #joinedChannel joinedChannel}), a message is
 * received from the server ({@link #receivedMessage
 * receivedMessage}).
 *
 * <p>If a server session becomes disconnected, it can no longer be
 * used to send messages to the server.  In this case, a client must
 * log in again to obtain a new server session to communicate with the
 * server.
 */
public interface ServerSessionListener {
    
    /**
     * Notifies this listener that its associated client has joined
     * the specified channel, and returns a {@link
     * ClientChannelListener} for that channel.
     *
     * <p>When a message is received on the specified channel, the
     * returned listener's {@link
     * ClientChannelListener#receivedMessage receivedMessage} method
     * is invoked with the specified channel, the sender's client
     * address, and the message.  A <code>null</code> sender indicates
     * that the message was sent by the server.  The returned listener
     * is <i>not</i> notified of messages that its client sends on the
     * specified channel.
     *
     * <p>When the client associated with this server session leaves
     * the specified channel, the returned listener's {@link
     * ClientChannelListener#leftChannel leftChannel} method is
     * invoked with the specified channel.
     *
     * @param channel a channel
     * @return a listener for the specified channel
     */
    ClientChannelListener joinedChannel(ClientChannel channel);
    
    /**
     * Notifies this listener that the specified message was sent by
     * the server.
     *
     * @param message a message
     */
    void receivedMessage(ByteBuffer message);

}
