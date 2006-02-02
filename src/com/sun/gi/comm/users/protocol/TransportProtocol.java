package com.sun.gi.comm.users.protocol;

import java.nio.ByteBuffer;
import java.io.IOException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

public interface TransportProtocol {

    public void packetReceived(ByteBuffer buff);

    public void sendLoginRequest() throws IOException;

    public void sendLogoutRequest() throws IOException;

    /**
     * Call this method from the client to send a unicast message
     */
    public void sendUnicastMsg(byte[] chanID, byte[] to, boolean reliable,
	ByteBuffer data) throws IOException;

    /**
     * Call this method from the server to deliver a unicast message to the
     * client
     */
    public void deliverUnicastMsg(byte[] chanID, byte[] from, byte[] to,
	boolean reliable, ByteBuffer data) throws IOException;

    /**
     * Call this method from the client to send a multicast message
     */
    public void sendMulticastMsg(byte[] chanID, byte[][] to, boolean reliable,
	ByteBuffer data) throws IOException;

    /**
     * Call this method from the server to deliver a multicast message to the
     * client
     */
    public void deliverMulticastMsg(byte[] chanID, byte[] from, byte[][] to,
	boolean reliable, ByteBuffer data) throws IOException;

    public void sendServerMsg(boolean reliable, ByteBuffer data)
	throws IOException;

    /**
     * Call this method from the client to send a multicast message
     */
    public void sendBroadcastMsg(byte[] chanID, boolean reliable,
	ByteBuffer data) throws IOException;

    /**
     * Call this method from the server to deliver a multicast message to the
     * client
     */
    public void deliverBroadcastMsg(byte[] chanID, byte[] from,
	boolean reliable, ByteBuffer data) throws IOException;

    /**
     * Call this method from the server to indcate successful login
     */
    public void deliverUserAccepted(byte[] newID) throws IOException;

    /**
     * Call this method from the server to indcate login failure
     */
    public void deliverUserRejected(String message) throws IOException;

    /**
     * 
     * Call this method from the client to attempt a fail-over reconnect
     */
    public void sendReconnectRequest(byte[] from, byte[] reconnectionKey)
	throws IOException;

    /**
     * Call this method from the server to request validation callback
     * information
     */
    public void deliverValidationRequest(Callback[] cbs)
	throws UnsupportedCallbackException, IOException;

    /**
     * Call this method from the client to send fileld out validation callbacks
     * to the server
     */
    public void sendValidationResponse(Callback[] cbs)
	throws UnsupportedCallbackException, IOException;

    /**
     * Call this method from the server to notify client of
     * newly logged on user
     */
    public void deliverUserJoined(byte[] user) throws IOException;

    /**
     * Call this method from the server to notify client of newly logged off
     * user
     */
    public void deliverUserLeft(byte[] user) throws IOException;

    /**
     * Call this method from the client to req a user be joiend to a channel
     */
    public void sendJoinChannelReq(String channelName) throws IOException;

    /**
     * Call this method from the server to notify client of user
     * joining channel
     */
    public void deliverUserJoinedChannel(byte[] chanID, byte[] user)
	throws IOException;

    /**
     * Call this method from the server to notify client of itself joining
     * channel
     */
    public void deliverJoinedChannel(String name, byte[] chanID)
	throws IOException;

    /**
     * Call this method from the client to leave a channel
     */
    public void sendLeaveChannelReq(byte[] chanID)
	throws IOException;

    /**
     * Call this method from the server to notify client of
     * user leaving channel
     */
    public void deliverUserLeftChannel(byte[] chanID, byte[] user)
	throws IOException;

    /**
     * Call this method from the server to notify client of itself leaving
     * channel
     */
    public void deliverLeftChannel(byte[] chanID) throws IOException;

    /**
     * Called when the server notifies the client that a
     * request to join/leave a channel failed due to the
     * channel being locked.
     * 
     * @param channelName   the name of the channel.
     * @param user	    the user
     * 
     * @throws IOException	
     */
    public void deliverChannelLocked(String channelName, byte[] user)
	throws IOException;

    /**
     * call this method from the server to send a reconenct key update to the
     * client
     */
    public void deliverReconnectKey(byte[] id, byte[] key, long ttl)
	throws IOException;

    public void setClient(TransportProtocolClient client);

    public void setServer(TransportProtocolServer server);

    public void setTransmitter(TransportProtocolTransmitter xmitter);

    public void deliverUserDisconnected(byte[] bs) throws IOException;

    public boolean isLoginPkt(ByteBuffer inputBuffer);
}
