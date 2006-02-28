package com.sun.gi.apps.mcs.matchmaker.client;

import java.util.HashMap;

public interface ILobbyChannelListener {
	
	public void playerEntered(byte[] player, String name);
	public void playerLeft(byte[] player);
	
	public void receiveText(byte[] from, String text, boolean wasPrivate);
	
	public void receivedGameParameters(HashMap<String, Object> parameters);
	
	public void createGameFailed(String name, String reason);
	
	public void gameCreated(GameDescriptor game);
	
	public void playerJoinedGame(GameDescriptor game, byte[] player);

}
