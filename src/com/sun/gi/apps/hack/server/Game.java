
/*
 * Game.java
 *
 * Created by: seth proctor (stp)
 * Created on: Mon Feb 20, 2006	 6:11:45 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimTask;

import java.nio.ByteBuffer;


/**
 * This is the generic interface for a game. A game is a place where users
 * can interact with each other and with the game's logic. Typically the games
 * that players use are the <code>Lobby</code> or a <code>Dungeon</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Game extends GLO
{

    /**
     * The standard namespace prefix for all games.
     */
    public static final String NAME_PREFIX = "game:";

    /**
     * Joins the given <code>Player</code> to this <code>Game</code>. The
     * player has been get-locked.
     *
     * @param player the <code>Player</code> joining this game
     */
    public void join(Player player);

    /**
     * Removes the given <code>Player</code> from this <code>Game</code>. The
     * player has been get-locked.
     *
     * @param player the <code>Player</code> joining this game
     */
    public void leave(Player player);

    /**
     * Creates a new instance of a <code>MessageHandler</code> that will
     * handle messages intended for this <code>Game</code>. The intent is
     * that this instance can be part of some other <code>GLO</code>'s
     * internal state, like a <code>Player</code>, so that contention is
     * moved there but is still being driven by logic defined by this
     * <code>Game</code> implementation.
     *
     * @return a new instance of <code>MessageHandler</code>
     */
    public MessageHandler createMessageHandler();

    /**
     * Returns the name of this <code>Game</code>.
     *
     * @return the name
     */
    public String getName();

    /**
     * Returns the number of players in this <code>Game</code>.
     *
     * @return the player count
     */
    public int numPlayers();

}
