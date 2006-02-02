package com.sun.gi.comm.users.protocol;

import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;

public interface TransportProtocolClient {

    void rcvUnicastMsg(boolean reliable, byte[] chanID,
	byte[] from, byte[] to, ByteBuffer databuff);

    void rcvMulticastMsg(boolean reliable, byte[] chanID,
	byte[] from, byte[][] tolist, ByteBuffer databuff);

    void rcvBroadcastMsg(boolean reliable, byte[] chanID,
	byte[] from, ByteBuffer databuff);

    void rcvValidationReq(Callback[] cbs);

    void rcvUserAccepted(byte[] user);

    void rcvUserRejected(String string);

    void rcvUserJoined(byte[] user);

    void rcvUserLeft(byte[] user);

    void rcvUserJoinedChan(byte[] chanID, byte[] user);

    void rcvUserLeftChan(byte[] chanID, byte[] user);

    void rcvReconnectKey(byte[] user, byte[] key, long ttl);

    void rcvJoinedChan(String chanName, byte[] chanID);

    void rcvLeftChan(byte[] chanID);

    void rcvUserDisconnected(byte[] chanID);

    /**
     * Called when the message was received from the server that an attempted
     * join/leave failed due to the target channel being locked.
     *
     * @param channelName   the name of the channel.
     * @param user	    the user ID that attempted the join/leave
     */
    void rcvChannelLocked(String channelName, byte[] user);

    /**
     * @param user
     */
    void recvServerID(byte[] user);
}
