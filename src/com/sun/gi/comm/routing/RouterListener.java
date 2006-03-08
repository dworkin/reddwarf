package com.sun.gi.comm.routing;

import java.nio.ByteBuffer;

import javax.security.auth.Subject;

/**
 * Listens for router messages that have to be propagated up to the
 * second tier of the server stack.
 * 
 * @author jeffpk
 */

public interface RouterListener {
    public void serverMessage(UserID from, ByteBuffer data, boolean reliable);

    public void userJoined(UserID uid, Subject subject);

    public void userLeft(UserID uid);

    public void userJoinedChannel(UserID uid, ChannelID cid);

    public void userLeftChannel(UserID uid, ChannelID cid);

    public void channelDataPacket(ChannelID cid, UserID from, ByteBuffer buff);
}
