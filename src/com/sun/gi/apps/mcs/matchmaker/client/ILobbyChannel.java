package com.sun.gi.apps.mcs.matchmaker.client;

import java.util.HashMap;

public interface ILobbyChannel {
	
	public void setListener(ILobbyChannelListener listener);
	
	public void sendText(String text);
	
	public void requestGameParameters();
	
	public void createGame(String name, String description, String password, HashMap<String, Object> gameParameters);

}
