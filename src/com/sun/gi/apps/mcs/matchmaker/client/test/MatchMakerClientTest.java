package com.sun.gi.apps.mcs.matchmaker.client.test;

import java.io.File;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;

import com.sun.gi.apps.mcs.matchmaker.client.FolderDescriptor;
import com.sun.gi.apps.mcs.matchmaker.client.ILobbyChannel;
import com.sun.gi.apps.mcs.matchmaker.client.IMatchMakingClient;
import com.sun.gi.apps.mcs.matchmaker.client.IMatchMakingClientListener;
import com.sun.gi.apps.mcs.matchmaker.client.LobbyDescriptor;
import com.sun.gi.apps.mcs.matchmaker.client.MatchMakingClient;
import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.SGSUUID;

/**
 * 
 * <p>Title: MatchMakerClientTest</p>
 * 
 * <p>Description: Test harness for the J2SE match making client.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class MatchMakerClientTest implements IMatchMakingClientListener {

	private IMatchMakingClient mmClient;
	
	private int numTimes = 0;
	
	public MatchMakerClientTest() {
	}
	
	public void connect() {
		try {
		    ClientConnectionManager manager = new ClientConnectionManagerImpl("MatchMakerTest",
			      new URLDiscoverer(
				  new File("resources/FakeDiscovery.xml").toURI().toURL()));
		    mmClient = new MatchMakingClient(manager);
		    mmClient.setListener(this);
		    String[] classNames = manager.getUserManagerClassNames();
		    manager.connect(classNames[0]);
		} catch (Exception e) {
		    e.printStackTrace();
		    return;
		}
	}
	
	public static void main(String[] args) {
		new MatchMakerClientTest().connect();
	}
	
	// implemented methods from IMatchMakingClientListener
	
	/**
	 * This call-back is called by the associated IMatchMakingClient in response to a listFolder command.
	 * 
	 * @param folderID			the UUID of the requested folder
	 * @param subFolders		an array of sub folders contained by the requested folder
	 * @param lobbies			an array of lobbies contained by the requested folder
	 */
	public void listedFolder(SGSUUID folderID, FolderDescriptor[] subFolders, LobbyDescriptor[] lobbies) {
		System.out.println("listed folder: folderID: " + folderID);
		for (FolderDescriptor f : subFolders) {
			System.out.println("Folder: " + f.getName() + " " + f.getDescription() + " " + f.getFolderID());
		}
		for (LobbyDescriptor l : lobbies) {
			System.out.println("Lobby: " + l.getName() + " " + l.getDescription() + " " + l.getNumUsers() + " " + l.getMaxUsers() + 
					l.isPasswordProtected() + " " + l.getLobbyID());
		}
		
		if (subFolders.length > 0 && numTimes == 0) {
			numTimes++;
			mmClient.listFolder(subFolders[0].getFolderID().toByteArray());
		}
		if(lobbies.length > 0) {
			//mmClient.joinLobby(lobbies[0].getLobbyID().toByteArray(), "secret");
		}
	}
	
	public void foundUserName(String userName, byte[] userID) {
		System.out.println("foundUserName: " + userName + " userID " + userID.toString());
	}
	
	public void joinedLobby(ILobbyChannel channel) {
		
	}
	
	public void connected(byte[] myID) {
		mmClient.listFolder(null);
		
		//mmClient.lookupUserName(myID);
	}
	
	public void validationRequest(Callback[] callbacks) {
    	System.out.println("validation request");
    	for (Callback cb : callbacks) {
    		if (cb instanceof NameCallback) {
    			((NameCallback) cb).setName("Guest");
    		}
    	}
    	mmClient.sendValidationResponse(callbacks);
	}

}
