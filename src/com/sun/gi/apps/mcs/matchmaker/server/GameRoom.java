package com.sun.gi.apps.mcs.matchmaker.server;

import java.util.HashMap;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.utils.SGSUUID;

/**
 * 
 * <p>Title: GameRoom</p>
 * 
 * <p>Description: Represents a Game Room in the match making application.  A GameRoom Channel name is
 * in the form of FolderName.SubFolderName.LobbyName:GameRoomName.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class GameRoom extends ChannelRoom {
	
	private static final long serialVersionUID = 1L;
	
	private UserID host; 
	
	public GameRoom(String name, String description, String password, String channelName, ChannelID cid, UserID host) {
		super(name, description, password, channelName, cid);
		
		this.host = host;
	}
	
	public SGSUUID getGameID() {
		return getChannelID();
	}
	
	public UserID getHost() {
		return host;
	}

}
