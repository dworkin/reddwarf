package com.sun.gi.apps.mcs.matchmaker.client;

import java.nio.ByteBuffer;

import com.sun.gi.apps.mcs.matchmaker.server.CommandProtocol;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;

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

	}

	public void channelClosed() {

	}

}
