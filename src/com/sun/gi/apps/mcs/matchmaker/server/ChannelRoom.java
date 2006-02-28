package com.sun.gi.apps.mcs.matchmaker.server;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.utils.SGSUUID;

/**
 * 
 * <p>Title: ChannelRoom</p>
 * 
 * <p>Description: Common super class for objects that are wrappers around Channels.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public abstract class ChannelRoom implements GLO {
	
	private String name;
	private String description;
	private String password;
	private ChannelID channelID;
	private String channelName;
	private List<UserID> playerList;
	

	public ChannelRoom(String name, String description, String password, String channelName, ChannelID cid) {
		this.name = name;
		this.description = description;
		this.password = password;
		this.channelName = channelName;
		this.channelID = cid;
		this.playerList = new LinkedList<UserID>();
	}
	
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getPassword() {
		return password;
	}
	
	public String getChannelName() {
		return channelName;
	}
	
	public void setChannelID(ChannelID cid) {
		this.channelID = cid;
	}
	
	public ChannelID getChannelID() {
		return channelID;
	}
	
	public boolean isPasswordProtected() {
		return password != null;
	}
	
	public void addUser(UserID user) {
		if (!playerList.contains(user)) {
			playerList.add(user);
		}
	}
	
	public void removeUser(UserID user) {
		playerList.remove(user);
	}
	
	public List<UserID> getUsers() {
		return playerList;
	}
	
	public void removeAllUsers() {
		playerList.clear();
	}
	
	public int getNumPlayers() {
		return playerList.size();
	}
	
}
