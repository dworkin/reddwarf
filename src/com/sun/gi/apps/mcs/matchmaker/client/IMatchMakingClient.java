package com.sun.gi.apps.mcs.matchmaker.client;

import java.util.UUID;

import javax.security.auth.callback.Callback;

import com.sun.gi.utils.SGSUUID;

/**
 * 
 * <p>Title: IMatchMakingClient</p>
 * 
 * <p>Description: This is the main interface that communicates with the server match making application.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public interface IMatchMakingClient {
	
	/**
	 * Sets the listener to receive the server command responses as call-backs.
	 * 
	 * @param listener			the listener
	 */
	public void setListener(IMatchMakingClientListener listener);
	
	/**
	 * Called to list the contents (folders and lobbies) of the folder with the matching Folder ID.  Maps
	 * to the server command, ListFolder.  The ListedFolder command response is
	 * expected from the server and translated to the IMatchMakingClientListener.listedFolder
	 * callback.
	 * 
	 * @param folderID			the folder's unique identifier.  Pass in null for the root listing.
	 */
	public void listFolder(byte[] folderID);
	
	/**
	 * Attempts to find the user name of a currently connected user with the given ID.
	 * 
	 * @param userID
	 */
	public void lookupUserName(byte[] userID);
	
	/**
	 * Attempts to find the user ID of a currently connected user with the given user name.
	 * 
	 * @param username
	 */
	public void lookupUserID(String username);
	
	public void joinLobby(byte[] lobbyID, String password);
	
	public void joinGame(byte[] gameID);
	
	public void joinGame(byte[] gameID, String password);
	
	/**
	 * Called as a pass-through to the ClientConnectionManager during login authentication.
	 * 
	 * @param cb			the security callbacks
	 */
	public void sendValidationResponse(Callback[] cb);

}
