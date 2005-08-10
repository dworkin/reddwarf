/*
 * SGSChannelListener.java
 *
 * Created on August 3, 2005, 5:03 PM
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
public interface SGSChannelListener {
    public void playerJoinedChannel(byte[] playerID);
    public void playerLeftChannel(byte[] playerID);
    public void receiveData(byte[] from, ByteBuffer data);
}
