package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

public interface ClientChannelListener {
	public void playerJoined(byte[] playerID);
	public void playerLeft(byte[] playerID);
	public void dataArrived(byte[] from, ByteBuffer data, boolean reliable);
}

