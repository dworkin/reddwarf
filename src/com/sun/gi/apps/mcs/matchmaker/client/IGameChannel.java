package com.sun.gi.apps.mcs.matchmaker.client;

public interface IGameChannel {
	
	public void setListener(IGameChannelListener listener);

	public void sendText(String text);
}
