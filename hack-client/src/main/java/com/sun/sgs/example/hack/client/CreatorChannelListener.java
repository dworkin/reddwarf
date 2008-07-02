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

import java.io.IOException;

import java.math.BigInteger;
import java.nio.ByteBuffer;


/**
 * This class listens for all messages from the creator game.
 */
public class CreatorChannelListener extends GameChannelListener
{

    // the listener that consumes creator messages
    private CreatorListener clistener;

    /**
     * Creates an instance of <code>CreatorChannelListener</code>.
     *
     * @param creatorListener listener for creator messages
     * @param chatListener listener for chat messages
     */
    public CreatorChannelListener(CreatorListener creatorListener,
                                  ChatListener chatListener) {
        super(chatListener);

        this.clistener = creatorListener;
    }

    /**
     * Notifies this listener that some data has arrived from a given
     * player. This should only be called with messages that pertain to
     * the creator.
     *
     * @param data the packet data
     * @param reliable true if this packet was sent reliably
     */
    public void receivedMessage(ClientChannel channel, 
                                ByteBuffer data) {

	// if this is a message from the server, then it's some
	// command that we need to process, so get the command code
	int command = (int)(data.get());

	// NOTE: in a more robut system, the listing of commands
	//       should really be an Enum
	try {
	    switch (command) {
	    case 0:
		// we got some uid to player name mapping
		addUidMappings(data);
		break;
	    case 1:
		// we got some new character stats
		int id = data.getInt();
		CharacterStats stats = (CharacterStats)(getObject(data));
		clistener.changeStatistics(id, stats);
		System.out.println("changed stats");
		break;
	    case 9:
		notifyJoinOrLeave(data, true);
		break;
	    case 8:
		notifyJoinOrLeave(data, true);
		break;
	    default:
		// someone must have sent us a chat message since
		// the first byte didn't start with a known
		// command
		notifyChatMessage(data);
	    }
	} catch (IOException ioe) {
	    // NOTE: this should probably handle the error a little
	    //       more gracefully, but it's unclear what the right
	    //       approach is
	    System.out.println("Failed to handle incoming creator object");
	    ioe.printStackTrace();
	}
    }

}
