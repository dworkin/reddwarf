package com.sun.gi.apps.mcs.matchmaker.client;

import java.util.HashMap;

import com.sun.gi.utils.SGSUUID;

public class GameDescriptor {
	
	private SGSUUID gameID;
	private String name;
	private String description;
	private boolean passwordProtected;
	private String channelName;
	private HashMap<String, Object> gameParameters;
	
	public GameDescriptor(SGSUUID id, String name, String desc, String channelName, boolean passwordProtected, HashMap<String, Object> params) {
		this.gameID = id;
		this.name = name;
		this.description = desc;
		this.channelName = channelName;
		this.passwordProtected = passwordProtected;
		this.gameParameters = params;
	}
	
	public SGSUUID getGameID() {
		return gameID;
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
	
	public String toString() {
		return name;
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof GameDescriptor)) {
			return false;
		}
		return ((GameDescriptor) o).getChannelName().equals(getChannelName());
	}
	
	public int hashCode() {
		return getChannelName().hashCode();
	}

}
