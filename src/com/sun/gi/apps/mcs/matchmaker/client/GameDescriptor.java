package com.sun.gi.apps.mcs.matchmaker.client;

import java.util.HashMap;

import com.sun.gi.utils.SGSUUID;

public class GameDescriptor {
	
	private SGSUUID lobbyID;
	private String name;
	private String description;
	private boolean passwordProtected;
	private String channelName;
	private HashMap<String, Object> gameParameters;
	
	public GameDescriptor(SGSUUID id, String name, String desc, String channelName, boolean passwordProtected, HashMap<String, Object> params) {
		this.lobbyID = id;
		this.name = name;
		this.description = desc;
		this.channelName = channelName;
		this.passwordProtected = passwordProtected;
		this.gameParameters = params;
	}
	
	public SGSUUID getLobbyID() {
		return lobbyID;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getChannelName() {
		return channelName;
	}
	
	public boolean isPasswordProtected() {
		return passwordProtected;
	}
	
	public HashMap<String, Object> getGameParameters() {
		return gameParameters;
	}

}
