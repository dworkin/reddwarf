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
import com.sun.sgs.example.hack.share.CreatureInfo;
import com.sun.sgs.example.hack.share.CreatureInfo.Creature;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.ItemInfo;
import com.sun.sgs.example.hack.share.ItemInfo.Item;
import com.sun.sgs.example.hack.share.ItemInfo.ItemType;
import com.sun.sgs.example.hack.share.SimpleCreature;
import com.sun.sgs.example.hack.share.SimpleItem;
import com.sun.sgs.example.hack.share.RoomInfo;
import com.sun.sgs.example.hack.share.RoomInfo.FloorType;

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
		addPlayerId(data);
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
		int numBoardSpaces = data.getInt();
		BoardSpace[] spaces = new BoardSpace[numBoardSpaces];

		for (int i = 0; i < numBoardSpaces; ++i)
		    spaces[i] = decodeBoardSpace(data);

		blistener.updateSpaces(spaces);
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

    private static BoardSpace decodeBoardSpace(ByteBuffer data) {

	int x = data.getInt();
	int y = data.getInt();
	int encodedFloorType = data.getInt();
	FloorType floorType = RoomInfo.decodeFloorType(encodedFloorType);

	BoardSpace space = new BoardSpace(x, y, floorType);

	// if the item name length is 0, then no item is present
	int itemNameLength = data.getInt();
	if (itemNameLength > 0) {
	    char[] arr = new char[itemNameLength];
	    for (int i = 0; i < itemNameLength; ++i) 
		arr[i] = data.getChar();
	    String itemName = new String(arr);
	    long itemId = data.getLong();
	    int encodedItemType = data.getInt();
	    ItemType itemType = ItemInfo.decodeItemType(encodedItemType);
	    space.setItem(new SimpleItem(itemType, itemId, itemName));	    
	}
	
	// the creature has a 0-length name, no creature is present
	int creatureNameLength = data.getInt();
	if (creatureNameLength > 0) {
	    char[] arr = new char[creatureNameLength];
	    for (int i = 0; i < creatureNameLength; ++i) 
		arr[i] = data.getChar();
	    String creatureName = new String(arr);
	    long creatureId = data.getLong();
	    int encodedCreatureType = data.getInt();
	    CreatureType creatureType = 
		CreatureInfo.decodeCreatureType(encodedCreatureType);
	    space.setCreature(new SimpleCreature(creatureType, 
						 creatureId, creatureName));
	}
	return space;
    }
    
}
