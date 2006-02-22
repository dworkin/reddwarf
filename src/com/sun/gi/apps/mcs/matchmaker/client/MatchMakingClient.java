package com.sun.gi.apps.mcs.matchmaker.client;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;

import com.sun.gi.apps.mcs.matchmaker.server.CommandProtocol;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

/**
 * 
 * <p>Title: MatchMakingClient</p>
 * 
 * <p>Description: This class is a concrete implementation of the IMatchMakingClient.  It
 * communicates with the match making server application via the UserManagerClient for
 * sending commands, and listening on the well known lobby control channel for receiving
 * responses.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class MatchMakingClient implements IMatchMakingClient, ClientConnectionManagerListener, ClientChannelListener {

	
	private IMatchMakingClientListener listener;
	private ClientConnectionManager manager;
	
	private CommandProtocol protocol;
	private byte[] myID;
	
	/**
	 * Constructs a new MatchMakingClient.  
	 * 
	 * @param userManager			the user manager used for server commmunication
	 */
	public MatchMakingClient(ClientConnectionManager manager) {
		this.manager = manager;
		manager.setListener(this);
		protocol = new CommandProtocol();
	}
	
	private void sendCommand(List list) {
		manager.sendToServer(protocol.assembleCommand(list), true);
	}
	
	private SGSUUID createUUID(byte[] bytes) {
		SGSUUID id = null;
		try {
			id = new StatisticalUUID(bytes);
		}
		catch (InstantiationException ie) {}
		
		return id;
	}
	
	public void setListener(IMatchMakingClientListener listener) {
		this.listener = listener;
	}

	public void listFolder(byte[] folderID) {
		List list = new LinkedList();
		list.add(CommandProtocol.LIST_FOLDER_REQUEST);
		if (folderID != null) {
			list.add(createUUID(folderID));
		}
		
		sendCommand(list);
	}
	
	public void joinLobby(byte[] lobbyID, String password) {
		List list = new LinkedList();
		list.add(CommandProtocol.JOIN_LOBBY);
		list.add(createUUID(lobbyID));
		if (password != null) {
			list.add(password);
		}
		
		sendCommand(list);
	}
	
	/**
	 * Attempts to find the user name of a currently connected user with the given ID.
	 * 
	 * @param userID
	 */
	public void lookupUserName(byte[] userID) {
		List list = new LinkedList();
		list.add(CommandProtocol.LOOKUP_USER_NAME_REQUEST);
		list.add(createUUID(userID));
		
		sendCommand(list);
	}
	
	public void sendValidationResponse(Callback[] cb) {
		manager.sendValidationResponse(cb);
	}
	
	// implemented methods from ClientConnectionManagerListener
	
    public void validationRequest(Callback[] callbacks) {
    	listener.validationRequest(callbacks);
    }

    public void connected(byte[] myID) {
    	System.out.println("connected");
    	this.myID = myID;
    	manager.openChannel(CommandProtocol.LOBBY_MANAGER_CONTROL_CHANNEL);
    }

    public void connectionRefused(String message) {
    	System.out.println("Connection Refused:  shouldn't have happened.");
    }

    public void failOverInProgress() {
    	
    }

    public void reconnected() {
    	
    }

    public void disconnected() {
    	System.out.println("Disconnected");
    }

    /**
     * This event is fired when a user joins the game.  When a client
     * initially connects to the game it will receive a userJoined
     * callback for every other user rpesent.  Aftre that every time a
     * new user joins, another callback will be issued.
     *
     * @param userID The ID of the joining user.
     */
    public void userJoined(byte[] userID) {
    	
    }

    /**
     * This event is fired whenever a user leaves the game.
     * This occurs either when a user purposefully disconnects or when
     * they drop and do not re-connect within the timeout specified
     * for the reconnection key in the Darkstar
     * backend.
     * <p>
     * <b>NOTE: In certain rare cases (such as the death of a slice),
     * notification may be delayed.  (In the slice-death case it is
     * delayed until a watchdog notices the dead slice.)</b>
     *
     * @param userID The ID of the user leaving the system.
     */
    public void userLeft(byte[] userID) {
    	
    }

    /**
     * This event is fired to notify the listener of sucessful
     * completion of a channel open operation.
     *
     * @param channel the channel object used to communicate on
     *                the opened channel.
     */
    public void joinedChannel(ClientChannel channel) {
    	System.out.println("Connected to channel " + channel.getName());
    	if (channel.getName().equals(CommandProtocol.LOBBY_MANAGER_CONTROL_CHANNEL)) {
    		channel.setListener(this);
    	}
    	listener.connected(myID);
    }

    /**
     * Called whenever an attempted join/leave fails due to the target
     * channel being locked.
     *
     * @param channelName	the name of the channel
     * @param userID		the ID of the user attemping to join/leave
     */
    public void channelLocked(String channelName, byte[] userID) {
    	
    }

    // implemented methods from ClientChannelListener
    // these call backs are on the LobbyManager control channel
    
    /**
     * A new player has joined the channel this listener is registered on.
     *
     * @param playerID The ID of the joining player.
     */
    public void playerJoined(byte[] playerID) {
    	
    }

    /**
     * A player has left the channel this listener is registered on.
     *
     * @param playerID The ID of the leaving player.
     */
    public void playerLeft(byte[] playerID) {
    	
    }

    /**
     * A packet has arrived for this listener on this channel.  Command
     * responses from the server come in on this channel.
     *
     * @param from     the ID of the sending player.
     * @param data     the packet data
     * @param reliable true if this packet was sent reliably
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
    	// ignore if not from server
    	// hmm, this doesn't seem to work
    	/*if (!manager.isServerID(from)) {
    		System.out.println("Not from server");
    		return;
    	}*/
    	int command = protocol.readUnsignedByte(data);
    	if (command == CommandProtocol.LIST_FOLDER_RESPONSE) {
    		listFolderResponse(data);
    	}
    	else if (command == CommandProtocol.LOOKUP_USER_NAME_RESPONSE) {
    		lookupUserNameResponse(data);
    	}
    }

    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {
    	
    }
    
    /**
     * Called to parse out the ListFolderResponse response from the server.
     * 
     * @param data		the data buffer containing the requested folder and lobby detail.
     */
    private void listFolderResponse(ByteBuffer data) {
    	SGSUUID folderID = protocol.readUUID(data);
    	int numFolders = data.getInt();
    	FolderDescriptor[] subfolders = new FolderDescriptor[numFolders];
    	for (int i = 0; i < numFolders; i++) {
    		String curFolderName = protocol.readString(data);
    		String curFolderDescription = protocol.readString(data);
    		SGSUUID curFolderID = protocol.readUUID(data);
    		subfolders[i] = new FolderDescriptor(curFolderID, curFolderName, curFolderDescription);
    	}
    	int numLobbies = data.getInt();
    	LobbyDescriptor[] lobbies = new LobbyDescriptor[numLobbies];
    	for (int i = 0; i < numLobbies; i++) {
    		String curLobbyName = protocol.readString(data);
    		String curLobbyDescription = protocol.readString(data);
    		SGSUUID curLobbyID = protocol.readUUID(data);
    		int numUsers = data.getInt();
    		int maxUsers = data.getInt();
    		boolean isPasswordProtected = protocol.readBoolean(data);
    		
    		lobbies[i] = new LobbyDescriptor(curLobbyID, curLobbyName, curLobbyDescription, numUsers, maxUsers, isPasswordProtected);
    	}
    	listener.listedFolder(folderID, subfolders, lobbies);
    }
    
    private void lookupUserNameResponse(ByteBuffer data) {
    	String userName = protocol.readString(data);
    	SGSUUID userID = protocol.readUUID(data);
    	
    	listener.foundUserName(userName, userID.toByteArray());
    }
    
}
