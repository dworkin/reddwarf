/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.client.ClientChannel;

import com.sun.sgs.example.hack.share.Commands;
import com.sun.sgs.example.hack.share.Commands.Command;
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


	int encodedCmd = (int)(data.getInt());
	Command cmd = Commands.decode(encodedCmd);

	try {
	    switch(cmd) {
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
	     * NOTE: During the charactor creation state, the server
	     * does not broadcast any player-specific messages on the
	     * channel.  These are instead sent directly to the client.
	     */

	    default:
		System.out.printf("Received unknown command %s (%d) on the " + 
				  "Creator channel%n", cmd, encodedCmd);
	    }
	}	
	catch (Throwable t) {
 	    // NOTE: this should probably handle the error a little
 	    //       more gracefully, but it's unclear what the right
 	    //       approach is
 	    t.printStackTrace();
 	}
    }

}
