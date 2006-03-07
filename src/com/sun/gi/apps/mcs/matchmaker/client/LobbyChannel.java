package com.sun.gi.apps.mcs.matchmaker.client;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.sun.gi.apps.mcs.matchmaker.server.CommandProtocol.*;

import com.sun.gi.apps.mcs.matchmaker.server.CommandProtocol;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.SGSUUID;

public class LobbyChannel implements ILobbyChannel, ClientChannelListener {

	private ClientChannel channel;
	private ILobbyChannelListener listener;
	private CommandProtocol protocol;
	private MatchMakingClient mmClient;
	
	public LobbyChannel(ClientChannel chan, MatchMakingClient client) {
		this.channel = chan;
		protocol = new CommandProtocol();
		this.mmClient = client;
	}
	
	public void setListener(ILobbyChannelListener listener) {
		this.listener = listener;
	}

	public void sendText(String text) {
		List list = protocol.createCommandList(SEND_TEXT);
		list.add(text);
		ByteBuffer buffer = protocol.assembleCommand(list);
		channel.sendBroadcastData(buffer, true);
	}

	// TODO sten: implement this
	public void sendPrivateText(byte[] user, String text) {
		List list = protocol.createCommandList(SEND_PRIVATE_TEXT);
		list.add(createUserID(user));
		list.add(text);
		ByteBuffer buffer = protocol.assembleCommand(list);
		
		System.out.println("LobbyChannel: Sending private text to " + user.length);
		channel.sendUnicastData(user, buffer, true);
	}
	
	private UserID createUserID(byte[] bytes) {
		UserID id = null;
		try {
			id = new UserID(bytes);
		}
		catch (InstantiationException ie) {}
		
		return id;
	}
	
	public void requestGameParameters() {
		System.out.println("LobbyChannel: requesting game params");
		List list = protocol.createCommandList(GAME_PARAMETERS_REQUEST);
		
		mmClient.sendCommand(list);
	}
	
	public void createGame(String name, String description, String password, HashMap<String, Object> gameParameters) {
		List list = protocol.createCommandList(CREATE_GAME);
		list.add(name);
		list.add(description);
		list.add(password != null);
		if (password != null) {
			list.add(password);
		}
		list.add(gameParameters.size());
		Iterator<String> iterator = gameParameters.keySet().iterator();
		while (iterator.hasNext()) {
			String curKey = iterator.next();
			list.add(curKey);
			Object value = gameParameters.get(curKey);
			list.add(protocol.mapType(value));
			list.add(value);
		}
		mmClient.sendCommand(list);
	}
	
	void receiveGameParameters(HashMap<String, Object> params) {
		if (listener != null) {
			listener.receivedGameParameters(params);
		}
	}
	
	void createGameFailed(String game, String reason) {
		if (listener != null) {
			listener.createGameFailed(game, reason);
		}
	}
	
	// implemented methods from ClientChannelListener
	
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
    	if (listener != null) {
    		listener.playerLeft(playerID);
    	}
    }

    /**
     * A packet has arrived for this listener on this channel.
     *
     * @param from     the ID of the sending player.
     * @param data     the packet data
     * @param reliable true if this packet was sent reliably
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
    	if (listener == null) {		// if no one is listening, no reason to do anything
    		return;
    	}
    	System.out.println("LobbyChannel.dataArrived " + from);
    	// TODO sten: test isServerID once that works
    	int command = protocol.readUnsignedByte(data);
    	if (command == PLAYER_ENTERED_LOBBY) {
    		SGSUUID userID = protocol.readUUID(data);
    		String name = protocol.readString(data);
    		listener.playerEntered(userID.toByteArray(), name);
    	}
    	else if (command == SEND_TEXT) {
    		listener.receiveText(from, protocol.readString(data), false);
    	}
    	else if (command == SEND_PRIVATE_TEXT) {
    		UserID id = protocol.readUserID(data);
    		listener.receiveText(from, protocol.readString(data), true);
    	}
    	else if (command == GAME_CREATED) {
    		SGSUUID uuid = protocol.readUUID(data);
    		String name = protocol.readString(data);
    		String description = protocol.readString(data);
    		String channelName = protocol.readString(data);
    		boolean isProtected = protocol.readBoolean(data);
    		int numParams = data.getInt();
        	HashMap<String, Object> paramMap = new HashMap<String, Object>();
        	for (int i = 0; i < numParams; i++) {
        		String param = protocol.readString(data);
        		Object value = protocol.readParamValue(data);
        		paramMap.put(param, value);
        	}
    		GameDescriptor game = new GameDescriptor(uuid, name, description, channelName, isProtected, paramMap);
    		listener.gameCreated(game);
    	}
    	else if (command == PLAYER_JOINED_GAME) {
    		SGSUUID userID = protocol.readUUID(data);
    		SGSUUID gameID = protocol.readUUID(data);
    		listener.playerJoinedGame(gameID.toByteArray(), userID.toByteArray());
    	}
    }

    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {
    	
    }
    
    public String getName() {
    	return channel.getName();
    }

}
