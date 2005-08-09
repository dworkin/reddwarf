/*
 * SGSChannel.java
 *
 * Created on August 3, 2005, 5:00 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

/**
 *
 * @author jeffpk
 */
public interface SGSChannel {
    public void addListener(SGSChannelListener listener);
    public void sendUnicast(byte[] to, ByteBuffer data, boolean reliable);
    public void sendMulticast(byte[][] to, ByteBuffer data, boolean reliable);
    public void send(ByteBuffer data, boolean reliable);
    public void leaveChannel();
    public String getChannelName();
}
