package com.sun.gi.comm.users.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;

import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;

public interface SGSUser {

    public void joinedChan(SGSChannel channel) throws IOException;

    public void leftChan(SGSChannel channel) throws IOException;

    public void msgReceived(byte[] channel, byte[] from,
	boolean reliable, ByteBuffer data) throws IOException;

    public void userJoinedSystem(byte[] user) throws IOException;

    public void userLeftSystem(byte[] user) throws IOException;

    public void userJoinedChannel(byte[] channelID, byte[] user)
	throws IOException;

    public void userLeftChannel(byte[] channel, byte[] user)
	throws IOException;

    public void reconnectKeyReceived(byte[] key, long ttl)
	throws IOException;

    UserID getUserID();

    public void deregistered();
}
