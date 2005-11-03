package com.sun.gi.comm.users.client.impl;

import java.nio.ByteBuffer;

import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;

/**
 * 
 * <p>Title: ClientChannelImpl
 * <p>Description: This file implements a channel in the client API</p>
 * <p>Copyright: Copyright (c) Oct 25, 2005 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public class ClientChannelImpl implements ClientChannel {
	byte[] ID;
	ClientConnectionManagerImpl mgr;
	String name;
	ClientChannelListener listener;
	
	/**
	 * Constructor
	 * @param manager The ClientConnectionManager that created this channel object
	 * @param channelID The Darkstar server ID of the channel this object represents,  
	 * This ID is local to this connection and is not gauranteed to be the same for otehr users
	 * on the same channel.
	 * 
	 */
	public ClientChannelImpl(ClientConnectionManagerImpl manager, String channelName, byte[] channelID) {
		ID = channelID;
		mgr = manager;
		name = channelName;
	}

	/**
	 * Returns the name of this channel.
	 * @return The name of the channel
	 */

	public String getName() {
		return name;
	}

	
	public void setListener(ClientChannelListener l) {
		listener = l;
		
	}

	public void sendUnicastData(byte[] to, ByteBuffer data, boolean reliable) {
		mgr.sendUnicastData(ID,to,data,reliable);
		
	}

	public void sendMulticastData(byte[][] to, ByteBuffer data, boolean reliable) {
		mgr.sendMulticastData(ID,to,data,reliable);
		
	}

	public void sendBroadcastData(ByteBuffer data, boolean reliable) {
		mgr.sendBroadcastData(ID,data,reliable);
		
	}

	public void channelClosed() {
		listener.channelClosed();
		
	}

	public void userJoined(byte[] userID) {
		listener.playerJoined(userID);
		
	}

	public void userLeft(byte[] userID) {
		listener.playerLeft(userID);
		
	}

	public void dataReceived(byte[] from, ByteBuffer data, boolean reliable) {
		listener.dataArrived(from,data,reliable);
		
	}

}
