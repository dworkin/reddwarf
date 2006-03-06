package com.sun.gi.apps.mcs.matchmaker.jme;

import com.sun.gi.utils.jme.SGSUUIDJMEImpl;


/**
 * 
 * <p>Title: LobbyDescriptor</p>
 * 
 * <p>Description: A simple, immutable, value object that represents the contents of a Lobby
 * in the match making application.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class JMELobbyDescriptor {

	private SGSUUIDJMEImpl lobbyID;
	private String name;
	private String description;
	private int numUsers;
	private int maxUsers;
	private boolean passwordProtected;
	
	public JMELobbyDescriptor(SGSUUIDJMEImpl id, String name, String desc, int numUsers, int maxUsers, boolean passwordProtected) {
		this.lobbyID = id;
		this.name = name;
		this.description = desc;
		this.numUsers = numUsers;
		this.maxUsers = maxUsers;
		this.passwordProtected = passwordProtected;
	}
	
	public SGSUUIDJMEImpl getLobbyID() {
		return lobbyID;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public int getNumUsers() {
		return numUsers;
	}
	
	public int getMaxUsers() {
		return maxUsers;
	}
	
	public boolean isPasswordProtected() {
		return passwordProtected;
	}
	
}
