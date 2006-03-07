package com.sun.gi.apps.mcs.matchmaker.client;

public interface IGameChannel {
	
	public String getName();
	
	public void setListener(IGameChannelListener listener);

	public void sendText(String text);
	
	public void ready(GameDescriptor game, boolean ready);
	
	public void startGame();
}
