package com.sun.gi.apps.mcs.matchmaker.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
	private HashMap<String, Object> gameParameters;
	private HashMap<UserID, Boolean> userMap;			// users mapped to ready state.
	
	public GameRoom(String name, String description, String password, String channelName, ChannelID cid, UserID host) {
		super(name, description, password, channelName, cid);
		
		userMap = new HashMap<UserID, Boolean>();
		
		this.host = host;
		gameParameters = new HashMap<String, Object>();
	}
	
	public SGSUUID getGameID() {
		return getChannelID();
	}
	
	public UserID getHost() {
		return host;
	}
	
	public void addGameParameter(String key, Object value) {
		gameParameters.put(key, value);
	}
	
	/**
	 * Returns a read-only view of the game parameters map.
	 * 
	 * @return	a read-only view of the game parameters map.
	 */
	public Map<String, Object> getGameParamters() {
		return Collections.unmodifiableMap(gameParameters);
	}
	
	public void addUser(UserID user) {
		if (!userMap.containsKey(user)) {
			userMap.put(user, false);
		}
	}
	
	/**
	 * Updates the "ready state" of the given user for this game.
	 * 
	 * @param user			the user to update
	 * @param ready			if true, the user is ready
	 */
	public void updateReady(UserID user, boolean ready) {
		userMap.put(user, ready);
	}
	
	public void removeUser(UserID user) {
		userMap.remove(user);
	}
	
	public List<UserID> getUsers() {
		List<UserID> playerList = new LinkedList<UserID>(userMap.keySet());
		
		return playerList;
	}
	
	public void removeAllUsers() {
		userMap.clear();
	}
	
	public int getNumPlayers() {
		return userMap.size();
	}
	
	/**
	 * Returns true if all joined players have indicated that they are ready.
	 * 
	 * @return	true if all players are ready for game start.
	 */
	public boolean arePlayersReady() {
		Iterator<UserID> iterator = userMap.keySet().iterator();
		while (iterator.hasNext()) {
			if (!userMap.get(iterator.next())) {
				return false;
			}
		}
		return true;
	}
	
}
