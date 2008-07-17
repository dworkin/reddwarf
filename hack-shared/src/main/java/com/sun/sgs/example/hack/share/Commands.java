/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;

import java.util.Map;
import java.util.HashMap;

public final class Commands {

    private static final Map<Integer,Commands.Command> ordinalToEnum =
	new HashMap<Integer,Commands.Command>();
    
    static {       
	for (Commands.Command cmd : Commands.Command.values()) {
	    ordinalToEnum.put(cmd.ordinal(), cmd);
	}
    }

    public static int encode(Commands.Command cmd) {
	return cmd.ordinal();
    }

    public static Commands.Command decode(int encodedType) {
	return ordinalToEnum.get(encodedType);
    }


    private Commands() { }

    public static enum Command {

	// game-state commands
	PLAYER_JOINED,
	    PLAYER_LEFT,
	    ADD_PLAYER_ID,
	    ADD_BULK_PLAYER_IDS,
	    
	    // lobby commands
	    UPDATE_AVAILABLE_GAMES,
	    UPDATE_GAME_MEMBER_COUNT,
	    GAME_ADDED,
	    GAME_REMOVED,
	    NOTIFY_PLAYABLE_CHARACTERS,

	    // dungeon commands
	    NEW_SPRITE_MAP,
	    NEW_BOARD,
	    UPDATE_BOARD_SPACES,
	    NEW_SERVER_MESSAGE,

	    // character (creator?) messages
	    NEW_CHARACTER_STATS
    }
    
}