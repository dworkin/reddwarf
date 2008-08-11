/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.client.ClientChannel;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.Commands;
import com.sun.sgs.example.hack.share.Commands.Command;

import java.awt.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;


/**
 * This class listens for all messages from a dungeon.
 */
public class DungeonChannelListener extends GameChannelListener
{

    // the listener that gets notified on incoming board-related messages
    private BoardListener blistener;

    // the listener that gets notified on incoming player-related messages
    private PlayerListener plistener;

    /**
     * Creates an instance of <code>DungeonChannelListener</code>.
     *
     * @param boardListener the listener for all board messages
     * @param chatListener the listener for all chat messages
     * @param playerListener the listener for all player messages
     */
    public DungeonChannelListener(BoardListener boardListener,
                                  ChatListener chatListener,
                                  PlayerListener playerListener) {
        super(chatListener);

        this.blistener = boardListener;
        this.plistener = playerListener;
    }

    /**
     * Notifies this listener that some data has arrived.  This should
     * only be called with messages that pertain to a dungeon.
     *
     * @param channel the channel on which the data arrive
     * @param data the data sent on the channel
     */
    public void receivedMessage(ClientChannel channel, 
                                ByteBuffer data) {

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

	    /*
	     * When a board space on the player's current level is
	     * changed, the server broadcasts the space's new contents
	     * to all the players in the dungeon.
	     */
	    case UPDATE_BOARD_SPACES:
		// we got some selective space updates
		@SuppressWarnings("unchecked")
                    Collection<BoardSpace> spaces =		    
		    (Collection<BoardSpace>)(getObject(data));
		BoardSpace [] s = new BoardSpace[spaces.size()];
		blistener.updateSpaces(spaces.toArray(s));
                break;

	    default:
		System.out.printf("Received unknown command %s (%d) on the " +
				  "Dungeon channel%n", cmd, encodedCmd);	
	    }
	} catch (IOException ioe) {
	    // NOTE: this should probably handle the error a little more
	    // gracefully, but it's unclear what the right approach is
	    System.out.println("Failed to handle incoming Dungeon object "
			       + "for command " + cmd);
	    ioe.printStackTrace();
	}
    }
}
