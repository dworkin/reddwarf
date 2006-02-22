package com.sun.gi.apps.mcs.matchmaker.client;

import javax.security.auth.callback.Callback;

import com.sun.gi.utils.SGSUUID;

/**
 * 
 * <p>Title: IMatchMakingClientListener</p>
 * 
 * <p>Description: Clients should implement this interface to receive command call-backs from 
 * the server application.  When the associated IMatchMakingClient receives a command response
 * from the server, it calls the appropriate call-back on this listener.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public interface IMatchMakingClientListener {
	
	/**
	 * This call-back is called by the associated IMatchMakingClient in response to a listFolder command.
	 * 
	 * @param folderID			the UUID of the requested folder
	 * @param subFolders		an array of sub folders contained by the requested folder
	 * @param lobbies			an array of lobbies contained by the requested folder
	 */
	public void listedFolder(SGSUUID folderID, FolderDescriptor[] subFolders, LobbyDescriptor[] lobbies);
	
	public void foundUserName(String userName, byte[] userID);
	
	public void joinedLobby(ILobbyChannel channel);
	
	/**
	 * Called when the client has successfully connected to the server and received confirmation
	 * that it is joined to the LobbyManager control channel.
	 */
	public void connected(byte[] myID);

	/**
	 * Called when a request for login authentication comes from the server.
	 * 
	 * @param callbacks		the security call backs
	 */
	public void validationRequest(Callback[] callbacks);

}
