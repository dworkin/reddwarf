package com.sun.gi.apps.mcs.matchmaker.server;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;
import com.sun.org.apache.bcel.internal.generic.LLOAD;

/**
 * 
 * <p>Title: Player</p>
 * 
 * <p>Description: This class represents a Player that is currently on-line.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class Player implements SimUserDataListener {
	
	private String userName;
	private UserID userID;
	private GLOReference folderRoot;
	private GLOReference currentLobby;
	private GLOReference currentGameRoom;
	
	private CommandProtocol protocol;
	
	public Player(UserID uid, String userName, GLOReference root) {
		this.userID = uid;
		this.userName = userName;
		this.folderRoot = root;
		
		protocol = new CommandProtocol();
	}
	
	public GLOReference getCurrentLobby() {
		return currentLobby;
	}
	
	/**
     * All command protocols for a connected user come in on this call back.  The 
     * command is parsed/processed and a response is sent out on the LobbyManager
     * control channel.
     * 
     * @see com.sun.gi.logic.SimUserDataListener#userDataReceived
     */
	public void userDataReceived(UserID from, ByteBuffer data) {
		SimTask task = SimTask.getCurrent();
		System.out.println("UserDataReceived from: " + from.toString());
		int commandCode = protocol.readUnsignedByte(data);
		if (commandCode == CommandProtocol.LIST_FOLDER_REQUEST) {
			listFolderRequest(task, data);
		}
		else if (commandCode == CommandProtocol.LOOKUP_USER_ID_REQUEST) {
			lookupUserIDRequest(task, data);
		}
		else if (commandCode == CommandProtocol.LOOKUP_USER_NAME_REQUEST) {
			lookupUserNameRequest(task, data);
		}
		else if (commandCode == CommandProtocol.FIND_USER_REQUEST) {
			locateUserRequest(task, data);
		}
		else if (commandCode == CommandProtocol.JOIN_LOBBY) {
			joinLobby(task, data);
		}
		else if (commandCode == CommandProtocol.JOIN_GAME) {
			joinGame(task, data);
		}
		else if (commandCode == CommandProtocol.GAME_PARAMETERS_REQUEST) {
			gameParametersRequest(task);
		}
		else if (commandCode == CommandProtocol.CREATE_GAME) {
			createGame(task, data);
		}
		
	}
	
    public void dataArrivedFromChannel(ChannelID id,
    		UserID from, ByteBuffer buff) {
    	
    }
	  
	/**
     * This callback is called when a user joins a channel.  This implementation also
     * passes the PlayerEnteredLobby command down the channel, which includes
     * the user's username.
     * 
     * @see com.sun.gi.logic.SimUserDataListener#userJoinedChannel
     */
	public void userJoinedChannel(ChannelID cid, UserID uid) {
		SimTask task = SimTask.getCurrent();
		if (uid.equals(userID)) {  // it was this player who joined, add to the either the lobby or game list.
			GLOMap<SGSUUID, GLOReference> lobbyMap = (GLOMap<SGSUUID, GLOReference>) task.findGLO("LobbyMap").peek(task);
			GLOReference lobbyRef = lobbyMap.get(cid);
			if (lobbyRef != null) {
				currentLobby = lobbyRef;
				Lobby lobby = (Lobby) lobbyRef.get(task);
				lobby.addUser(uid);
				
				// send lobby joined message
				List list = new LinkedList();
				list.add(CommandProtocol.PLAYER_ENTERED_LOBBY);
				list.add(userID);
				list.add(userName);
				sendResponse(task, list, cid);
			}
			else {			// must have been a game room
				GLOMap<SGSUUID, GLOReference> gameRoomMap = (GLOMap<SGSUUID, GLOReference>) task.findGLO("GameRoomMap").peek(task);
				GLOReference gameRef = gameRoomMap.get(cid);
				if (gameRef != null) {
					GameRoom gameRoom = (GameRoom) gameRef.get(task);
					gameRoom.addUser(uid);
					
					// send playerJoinedGame message to lobby
					List lobbyList = new LinkedList();
					lobbyList.add(CommandProtocol.PLAYER_JOINED_GAME);
					lobbyList.add(uid);
					lobbyList.add(gameRoom.getGameID());
					
					// TODO sten: assumes that player keeps lobby ref.  verify.
					Lobby lobby = (Lobby) currentLobby.peek(task);
					sendResponse(task, lobbyList, lobby.getChannelID());
					
					// send playerEnteredGame to game room
					List grList = new LinkedList();
					grList.add(CommandProtocol.PLAYER_ENTERED_GAME);
					grList.add(uid);
					grList.add(userName);
					
					sendResponse(task, grList, gameRoom.getChannelID());
				}

			}

		}

	}
	 
	/**
     * <p>Called when a user leaves a channel.  There's only work to do if the user leaving is
     * the player.  If so, they either left a lobby or a game room.</p>
     * 
     * <p>If they left a Lobby, simply remove them from the Lobby's list of users.</p>
     * 
     * <p>If they left a Game Room, things are a bit more involved.  The player is removed from 
     * the Game Room's user list, and a message is sent out to the Lobby that the player left
     * the game.  Additionally, if the player leaving is the game host, then the game is shut down.
     * Shutting down the game involves "leaving" all the joined players (which results in more 
     * PlayerLeftGame messages to the Lobby), and then sending out a GameDeleted message to the 
     * Lobby.</p>
     * 
     * @see com.sun.gi.logic.SimUserDataListener#userLeftChannel
     */
	public void userLeftChannel(ChannelID cid, UserID uid) {
		SimTask task = SimTask.getCurrent();
		if (uid.equals(userID)) {  // it was this player who left, cleanup -- remove from any lists.
			if (currentGameRoom != null) {		// the player left a game room
				GameRoom gameRoom = (GameRoom) currentGameRoom.get(task);
				if (cid.equals(gameRoom.getChannelID())) {		// user left from this channel
					gameRoom.removeUser(uid);
					
					Lobby lobby = (Lobby) currentLobby.get(task);
					
					// if this was the host, kick everyone out and kill the game
					if (gameRoom.getHost().equals(uid)) {
						for (UserID curPlayer : gameRoom.getUsers()) {
							task.leave(curPlayer, gameRoom.getChannelID());
						}
						gameRoom.removeAllUsers();
						
						// Send notification to the Lobby that the game was killed
						List list = new LinkedList();
						list.add(CommandProtocol.GAME_DELETED);
						list.add(gameRoom.getGameID());
						
						sendResponse(task, list, lobby.getChannelID());
					}
					
					currentGameRoom.delete(task);
					currentGameRoom = null;
					
					// rejoin lobby
					task.join(uid, lobby.getChannelID());
					
					// send PlayerLeftGame message to lobby
					List list = new LinkedList();
					list.add(CommandProtocol.PLAYER_LEFT_GAME);
					list.add(uid);
					list.add(gameRoom.getGameID());
					
					sendResponse(task, list, lobby.getChannelID());
				}
			}
			else if (currentLobby != null) {
				Lobby lobby = (Lobby) currentLobby.get(task);
				lobby.removeUser(uid);
				currentLobby.delete(task);
				currentLobby = null;
			}
		}
	}
	
	public String getUserName() {
		return userName;
	}
	
	/**
	 * Responds to the ListFolderRequest command protocol.  Reads the requested FolderID off the
	 * buffer and attempts to find the folder with the matching ID.  If found, the subfolders of this
	 * folder are listed as well as any lobbies.
	 * 
	 * @param task
	 * @param data
	 */
	private void listFolderRequest(SimTask task, ByteBuffer data) {
		SGSUUID folderID = null;
		if (data.hasRemaining()) {
			folderID = protocol.readUUID(data);
		}
		Folder root = (Folder) folderRoot.peek(task);
		Folder targetFolder = folderID == null ? root : root.findFolder(task, folderID); 
		System.out.println("folderID = " + folderID + " targetFolder " + targetFolder.getName());
		List list = new LinkedList();
		list.add(CommandProtocol.LIST_FOLDER_RESPONSE);
		list.add(folderID == null ? root.getFolderID() : folderID);
		if (targetFolder != null) {
			list.add(targetFolder.getFolders().size());
			for (GLOReference folderRef : targetFolder.getFolders()) {
				Folder curFolder = (Folder) folderRef.peek(task);
				
				// list out the contents of the current folder
				list.add(curFolder.getName());
				list.add(curFolder.getDescription());
				list.add(curFolder.getFolderID());
				
			}
			
			// finally list out the lobbies.
			List<GLOReference> lobbyList = targetFolder.getLobbies();
			list.add(lobbyList.size());
			for (GLOReference lobbyRef : lobbyList) {
				Lobby curLobby = (Lobby) lobbyRef.peek(task);
				
				list.add(curLobby.getName());
				list.add(curLobby.getDescription());
				list.add(curLobby.getLobbyID());
				list.add(curLobby.getNumPlayers());
				list.add(curLobby.getMaxPlayers());
				list.add(curLobby.isPasswordProtected());
			}
		}
		else {  // return a zero size
			list.add(0);
		}
		sendResponse(task, list);
	}
	
	/**
	 * Attempts to lookup a connected Player by their user name.  The Player's ID is returned.
	 * 
	 * @param task
	 * @param data
	 */
	private void lookupUserIDRequest(SimTask task, ByteBuffer data) {
		String username = protocol.readString(data);
		UserID id = null;
		if (username != null) {
			GLOMap<String, UserID> userMap = (GLOMap<String, UserID>) task.findGLO("UsernameMap").peek(task);
			id = userMap.get(username);
		}
		
		List list = new LinkedList();
		list.add(CommandProtocol.LOOKUP_USER_ID_RESPONSE);
		list.add(username);
		list.add(id);
		
		sendResponse(task, list);
	}
	
	private void lookupUserNameRequest(SimTask task, ByteBuffer data) {
		UserID id = protocol.readUserID(data);
		String username = null;
		if (id != null) {
			GLOReference pRef = task.findGLO(id.toString());
			if (pRef != null) {
				Player player = (Player) pRef.get(task);
				username = player.getUserName();
			}
		}
		
		List list = new LinkedList();
		list.add(CommandProtocol.LOOKUP_USER_NAME_RESPONSE);
		list.add(username);
		list.add(id);
		
		sendResponse(task, list);
	}
	
	/**
	 * Attempts to find the lobby that the requested user is connected to if any.
	 * 
	 * @param task			the SimTask
	 * @param data			the data buffer
	 */
	private void locateUserRequest(SimTask task, ByteBuffer data) {
		UserID requestedID = protocol.readUserID(data);
		GLOReference requestedRef = task.findGLO(requestedID.toString());
		Lobby lobby = null;
		if (requestedRef != null) {
			Player requestedPlayer = (Player) requestedRef.peek(task);
			if (requestedPlayer.getCurrentLobby() != null) {
				lobby = (Lobby) requestedPlayer.getCurrentLobby().peek(task);
			}
		}
		List list = new LinkedList();
		list.add(CommandProtocol.LOCATE_USER_RESPONSE);
		list.add(requestedID);
		list.add(lobby != null ? 1 : 0);			// number of lobbies -- only one allowed
		if (lobby != null) {
			list.add(lobby.getName());
			list.add(lobby.getDescription());
			list.add(lobby.getLobbyID());
			list.add(lobby.getNumPlayers());
			list.add(lobby.getMaxPlayers());
			list.add(lobby.isPasswordProtected());
		}
		sendResponse(task, list);
	}
	
	/**
	 * Attempts to join this user to the Lobby channel specified in the 
	 * data buffer.  If the lobby is password protected, then a password
	 * is CommandProtocol.read off the buffer and compared.
	 * 
	 * @param task			the SimTask
	 * @param data			the buffer containing the command parameters
	 */
	private void joinLobby(SimTask task, ByteBuffer data) {
		// TODO sten: perhaps this should send a response instead of silently returning
		if (currentLobby != null) {
			return;
		}
		
		SGSUUID lobbyID = protocol.readUUID(data);
		GLOMap<SGSUUID, GLOReference> lobbyMap = (GLOMap<SGSUUID, GLOReference>) task.findGLO("LobbyMap").peek(task);
		GLOReference lobbyRef = lobbyMap.get(lobbyID);
		if (lobbyRef != null) {
			Lobby lobby = (Lobby) lobbyRef.peek(task);
			if (lobby.getNumPlayers() == lobby.getMaxPlayers()) {
				// TODO sten: perhaps in the future spawn a new lobby. 
				return;
			}
			if (lobby.isPasswordProtected()) {
				String password = protocol.readString(data);
				if (lobby.getPassword().equals(password)) {
					task.join(userID, lobby.getChannelID());
				}
			}
			else {
				task.join(userID, lobby.getChannelID());
			}
			
		}
		else {
			//TODO sten: return an error if lobby not found, or if passwords don't match?
		}
	}
	
	/**
	 * Attempts to join this user to the Game Room channel specified in the 
	 * data buffer.  If the game is password protected, then a password
	 * is CommandProtocol.read off the buffer and compared.
	 * 
	 * @param task			the SimTask
	 * @param data			the buffer containing the command parameters
	 */
	private void joinGame(SimTask task, ByteBuffer data) {
		if (currentGameRoom != null) {		// can't connect if alCommandProtocol.ready connected to a game.
			return;
		}
		SGSUUID gameID = protocol.readUUID(data);
		
		GLOMap<SGSUUID, GLOReference> gameRoomMap = (GLOMap<SGSUUID, GLOReference>) task.findGLO("GameRoomMap").peek(task);
		GLOReference gameRef = gameRoomMap.get(gameID);
		if (gameRef == null) {
			return;
		}
		GameRoom gameRoom = (GameRoom) gameRef.peek(task);
		if (gameRoom.isPasswordProtected()) {
			String password = protocol.readString(data);
			if (!password.equals(gameRoom.getPassword())) {
				return;
			}
		}
		task.join(userID, gameRoom.getChannelID());
	}
	
	/**
	 * Called when a user requests the parameters for a game on the 
	 * currently connected lobby.  The GameParametersResponse command 
	 * is sent as the response.
	 * 
	 * @param task			the SimTask
	 */
	private void gameParametersRequest(SimTask task) {
		List list = new LinkedList();
		list.add(CommandProtocol.GAME_PARAMETERS_RESPONSE);
		if (currentLobby != null) {
			Lobby lobby = (Lobby) currentLobby.peek(task);
			Map<String, Object> gameParameters = lobby.getGameParamters();
			list.add(gameParameters.size());
			Iterator iterator = gameParameters.keySet().iterator();
			while (iterator.hasNext()) {
				String curKey = (String) iterator.next();
				list.add(curKey);
				Object value = gameParameters.get(curKey);
				list.add(protocol.mapType(value));
				list.add(value);
				
			}
		}
		
		sendResponse(task, list);
	}
	
	/**
	 * <p>Processes a request to create a new game room in the user's current lobby.
	 * If the game can not be created for any reason, a CreateGameFailed response is
	 * sent back to the user with a reason for the failure.</p>
	 * 
	 * <p>If creation is successful, the new GameRoom object is added to the lobby 
	 * and connected users are notified via the GameCreated response.</p> 
	 * 
	 * @param task				the SimTask
	 * @param data				the buffer containing the request data
	 */
	private void createGame(SimTask task, ByteBuffer data) {
		String gameName = protocol.readString(data);
		if (currentLobby == null) {			// bail out early if not connected.
			sendGameCreateFailedResponse(task, gameName, "Not connected to a lobby");
			return;
		}
		
		String description = protocol.readString(data);
		boolean hasPassword = protocol.readBoolean(data);
		String password = null;
		if (hasPassword) {
			password = protocol.readString(data);
		}

		int numParams = data.getInt();
		HashMap<String, Object> gameParams = new HashMap<String, Object>();
		for (int i = 0; i < numParams; i++) {
			gameParams.put(protocol.readString(data), protocol.readParamValue(data));
		}
		
		Lobby lobby = (Lobby) currentLobby.get(task);
		Map<String, Object> lobbyParameters = lobby.getGameParamters();
		
		// bail out if all of the expected parameters are not present.
		if (gameParams.keySet().equals(lobbyParameters.keySet())) {
			sendGameCreateFailedResponse(task, gameName, "Parameters mis-match.");
			return;
		}
		
		String channelName = lobby.getChannelName() + ":" + gameName;
		ChannelID cid = task.openChannel(channelName);
		task.lock(cid, true);	// game access is controled by the server
		GameRoom gr = new GameRoom(gameName, description, password, channelName, cid, userID);
		GLOReference grRef = task.createGLO(gr);
		lobby.addGameRoom(grRef);
		
		// add to the game room map for easy look-up by gameID
		GLOMap<SGSUUID, GLOReference> gameRoomMap = (GLOMap<SGSUUID, GLOReference>) task.findGLO("GameRoomMap").get(task);
		gameRoomMap.put(gr.getGameID(), grRef);
		
		List list = new LinkedList();
		list.add(CommandProtocol.GAME_CREATED);
		list.add(cid);
		list.add(gameName);
		list.add(description);
		list.add(channelName);
		list.add(password != null);
		list.add(numParams);
		
		Iterator iterator = gameParams.keySet().iterator();
		while (iterator.hasNext()) {
			String curKey= (String) iterator.next();
			list.add(curKey);
			Object value = gameParams.get(curKey);
			
			list.add(protocol.mapType(value));
			list.add(value);
		}
		
		sendResponse(task, list, lobby.getChannelID());
		
	}
	
	private void sendGameCreateFailedResponse(SimTask task, String gameName, String message) {
		List list = new LinkedList();
		list.add(CommandProtocol.CREATE_GAME_FAILED);
		list.add(gameName);
		list.add(message);
		
		sendResponse(task, list);
	}
	
	private void sendResponse(SimTask task, List list) {
		ChannelID cid = task.openChannel(CommandProtocol.LOBBY_MANAGER_CONTROL_CHANNEL);
		
		sendResponse(task, list, cid);
	}
	
	private void sendResponse(SimTask task, List list, ChannelID cid) {
		ByteBuffer data = protocol.assembleCommand(list);
		
		// TODO sten: refactor sendData for unicast (or not)
		task.sendData(cid, new UserID[] {userID}, data, true);
	}
	

}

