package com.sun.gi.apps.mcs.matchmaker.client;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.sun.gi.apps.mcs.matchmaker.server.CommandProtocol.*;

import com.sun.gi.apps.mcs.matchmaker.server.CommandProtocol;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.SGSUUID;

public class GameChannel implements IGameChannel, ClientChannelListener {
	
	private IGameChannelListener listener;
	private MatchMakingClient mmClient;
	private ClientChannel channel;
	private CommandProtocol protocol;
	
	public GameChannel(ClientChannel chan, MatchMakingClient client) {
		this.channel = chan;
		protocol = new CommandProtocol();
		this.mmClient = client;
	}

	public void setListener(IGameChannelListener listener) {
		this.listener = listener;
	}

	public void sendText(String text) {
		
	}

	public void playerJoined(byte[] playerID) {
	}

	public void playerLeft(byte[] playerID) {

	}

	public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
    	if (listener == null) {		// if no one is listening, no reason to do anything
    		return;
    	}
    	System.out.println("GameChannel.dataArrived " + from);
    	// TODO sten: test isServerID once that works
    	int command = protocol.readUnsignedByte(data);
    	if (command == PLAYER_ENTERED_GAME) {
    		SGSUUID userID = protocol.readUUID(data);
    		String name = protocol.readString(data);
    		listener.playerEntered(userID.toByteArray(), name);
    	}
    	else if (command == PLAYER_READY_UPDATE) {
    		SGSUUID userID = protocol.readUUID(data);
    		boolean ready = protocol.readBoolean(data);
    		listener.playerReady(userID.toByteArray(), ready);
    	}
    	else if (command == START_GAME_REQUEST) {		// means there was a failure.
    		System.out.println("game channel: start game failed");
    		listener.startGameFailed(protocol.readString(data));
    	}
    	else if (command == GAME_STARTED) {
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
    		listener.gameStarted(game);
    	}

	}

	public void channelClosed() {

	}
	
	public String getName() {
		return channel.getName();
	}
	
	public void ready(GameDescriptor game, boolean ready) {
		List list = protocol.createCommandList(UPDATE_PLAYER_READY_REQUEST);
		list.add(ready);
		if (ready) {
			HashMap<String, Object> gameParameters = game.getGameParameters();
			list.add(gameParameters.size());
			Iterator<String> iterator = gameParameters.keySet().iterator();
			while (iterator.hasNext()) {
				String curKey = iterator.next();
				list.add(curKey);
				Object value = gameParameters.get(curKey);
				list.add(protocol.mapType(value));
				list.add(value);
			}
		}
		mmClient.sendCommand(list);
	}
	
	public void startGame() {
		List list = protocol.createCommandList(START_GAME_REQUEST);
		mmClient.sendCommand(list);
	}

}
