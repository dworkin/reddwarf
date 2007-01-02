package com.sun.sgs.client;

/**
 * A client's listener for handling messages sent from server to
 * client and for handling other connection-related events.
 *
 * <p>A <code>ServerSessionListener</code> for a client is notified in
 * the following cases: when the associated client is joined to a
 * channel ({@link #joinedChannel joinedChannel}), a message is
 * received from the server ({@link #receivedMessage
 * receivedMessage}), a connection with the server is being
 * re-established ({@link #reconnecting reconnecting}), a connection
 * has been re-established ({@link #reconnected reconnected}), or
 * finally when the associated server session becomes disconnected,
 * gracefully or otherwise ({@link #disconnected disconnected}).
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
     * is invoked with the specified channel, the sender's session
     * identifier, and the message.  A <code>null</code> sender indicates
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
    void receivedMessage(byte[] message);
    
    /**
     * Notifies this listener that its associated server session is in
     * the process of reconnecting with the server.
     *
     * <p>If a connection can be re-established with the server in a
     * timely manner, this listener's {@link #reconnected reconnected}
     * method will be invoked.  Otherwise, if a connection cannot be
     * re-established, this listener's <code>disconnected</code>
     * method will be invoked with <code>false</code> indicating that
     * the associated session is disconnected from the server and the
     * client must log in again.
     */
    void reconnecting();

    /**
     * Notifies this listener whether the associated server session is
     * successfully reconnected. 
     */
    void reconnected();
    
    /**
     * Notifies this listener that the associated server session is
     * disconnected.
     *
     * <p>If <code>graceful</code> is <code>true</code>, the
     * disconnection was due to the associated client gracefully
     * logging out; otherwise, the disconnection was due to other
     * circumstances, such as forced disconnection.
     *
     * @param graceful <code>true</code> if disconnection was due to
     * the associated client gracefully logging out, and
     * <code>false</code> otherwise
     *
     * TODO perhaps this should additionally take byte[] message bubbled
     * up from the ClientConnectionListener with the reason for disconnection.
     * For example, the protocol versions may not match.
     */
    void disconnected(boolean graceful);
}
