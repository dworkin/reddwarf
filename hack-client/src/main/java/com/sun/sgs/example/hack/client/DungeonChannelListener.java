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

	// if this is a message from the server, then it's some
	// command that we need to process, so get the command code
	int command = (int)(data.get());

	// NOTE: in a more robust implementation, the commands used
	//       should really be an enumeration
	try {
	    switch (command) {
	    case 0:
		// we got some uid to player name mapping
		addUidMappings(data);
		break;
	    case 1:
		// we were sent updated character statistics
		int id = data.getInt();
		CharacterStats stats = (CharacterStats)(getObject(data));
		plistener.setCharacter(id, stats);
		break;
	    case 8:
		notifyJoinOrLeave(data, true);
		break;
	    case 9:
		notifyJoinOrLeave(data, true);
		break;

	    case 21:
		// we were sent game membership updates
		int spriteSize = data.getInt();
		@SuppressWarnings("unchecked")
                    Map<Integer,byte[]> spriteMap =
		    (Map<Integer,byte[]>)(getObject(data));
		blistener.setSpriteMap(spriteSize, convertMap(spriteMap));
                break;
	    case 22:
		// we got a complete board update
		Board board = (Board)(getObject(data));
		blistener.changeBoard(board);
                break;
	    case 23:
		// we got some selective space updates
		@SuppressWarnings("unchecked")
                    Collection<BoardSpace> spaces =
		    (Collection<BoardSpace>)(getObject(data));
		BoardSpace [] s = new BoardSpace[spaces.size()];
		blistener.updateSpaces(spaces.toArray(s));
                break;
	    case 24:
		// we heard some message from the server
		byte [] bytes = new byte[data.remaining()];
		data.get(bytes);
		String msg = new String(bytes);
		blistener.hearMessage(msg);
		break;
	    default:
		// someone must have sent us a chat message since
		// the first byte didn't start with a known
		// command
		notifyChatMessage(data);
	    }
	} catch (IOException ioe) {
	    // NOTE: this should probably handle the error a little more
	    // gracefully, but it's unclear what the right approach is
	    System.out.println("Failed to handle incoming Dungeon object "
			       + "for command " + command);
	    ioe.printStackTrace();
	}
    }

    /**
     * A private helper that converts the map from the server (that
     * maps integers to byte arrays) into the form needed on the
     * client (that maps integers to images). The server sends the
     * byte array form because images aren't serializable.
     */
    private Map<Integer,Image> convertMap(Map<Integer,byte[]> map) {
        Map<Integer,Image> newMap = new HashMap<Integer,Image>();

        // for each of the identified sprites, try to load the bytes
        // as a recognizable image format and store in the new map
        for (int identifier : map.keySet()) {
            try {
                ByteArrayInputStream in =
                    new ByteArrayInputStream(map.get(identifier));
                newMap.put(identifier, ImageIO.read(in));
            } catch (IOException ioe) {
                System.out.println("Failed to convert image: " + identifier);
                ioe.printStackTrace();
            }
        }

        return newMap;
    }

}
