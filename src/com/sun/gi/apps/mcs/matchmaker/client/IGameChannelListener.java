package com.sun.gi.apps.mcs.matchmaker.client;

public interface IGameChannelListener {
	
	public void playerEntered(byte[] player, String name);
	public void playerLeft(byte[] player);
	
	public void receiveText(byte[] from, String text, boolean wasPrivate);

}
