/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.client.ClientChannel;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import com.sun.sgs.example.hack.share.Commands;
import com.sun.sgs.example.hack.share.Commands.Command;

import java.io.IOException;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.Map;


/**
 * This class listens for all messages from the lobby.
 */
public class LobbyChannelListener extends GameChannelListener {

    // the listener that gets notified on incoming messages
    private LobbyListener llistener;

    /**
     * Creates an instance of <code>LobbyListener</code>.
     *
     * @param lobbyListener the listener for all lobby messages
     * @param chatListener the listener for all chat messages
     */
    public LobbyChannelListener(LobbyListener lobbyListener,
                                ChatListener chatListener) {
        super(chatListener);

        this.llistener = lobbyListener;
    }

    /**
     * Notifies this listener that some data has arrived from a given
     * player. This should only be called with messages that pertain to
     * the lobby.
     *
     * @param channel the channel on which this data was received
     * @param data the data received
     */
    public void receivedMessage(ClientChannel channel, ByteBuffer data) {

	int encodedCmd = (int)(data.getInt());
	Command cmd = Commands.decode(encodedCmd);

	try {
	    switch (cmd) {

	    case ADD_PLAYER_ID:
		@SuppressWarnings("unchecked")
		BigInteger playerID = (BigInteger)(getObject(data));
		@SuppressWarnings("unchecked")
		String playerName = (String)(getObject(data));
		addPlayerIdMapping(playerID, playerName);
		break;

	    case PLAYER_JOINED:
		notifyJoin(data);
		break;
		
	    case PLAYER_LEFT:
		notifyLeave(data);
		break;
				
	    case UPDATE_GAME_MEMBER_COUNT: {

		// we got a membership count update for some game
		Object[] countAndName = (Object[])(getObject(data));
		Integer count = (Integer)(countAndName[0]);
		String name = (String)(countAndName[1]);
                
		// see if it's the lobby or some specific dungeon, and
		// update the count appropriately
		if (name.equals("game:lobby"))
		    llistener.playerCountUpdated(count);
		    else
			llistener.playerCountUpdated(name, count);
	    }

	    case GAME_ADDED: {
		// we heard about a new game
		byte [] bytes = new byte[data.remaining()];
		data.get(bytes);
		llistener.gameAdded(new String(bytes));
		break; 
	    }

	    case GAME_REMOVED: {
		// we heard that a game was removed
		byte [] bytes = new byte[data.remaining()];
		data.get(bytes);
		llistener.gameRemoved(new String(bytes));
		break; 
	    }

	    default:
		System.out.printf("Received unknown command %s (%d) on the " + 
				  "Lobby channel%n", cmd, encodedCmd);		
	    }
	} catch (IOException ioe) {
	    // NOTE: this should probably handle the error a little more
	    // gracefully, but it's unclear what the right approach is
	    System.out.println("Failed to handle incoming Lobby object");
	    ioe.printStackTrace();
	}
    }

}
