package com.sun.gi.comm.users.protocol;

import java.nio.*;

import javax.security.auth.callback.*;


public interface TransportProtocolClient {

	void rcvUnicastMsg(boolean reliable, byte[] chanID, byte[] from, byte[] to, ByteBuffer databuff);

	void rcvMulticastMsg(boolean reliable, byte[] chanID, byte[] from, byte[][] tolist, ByteBuffer databuff);

	void rcvBroadcastMsg(boolean reliable, byte[] chanID, byte[] from, ByteBuffer databuff);

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

 

}