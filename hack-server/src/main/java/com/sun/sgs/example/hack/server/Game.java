/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.ManagedObject;


/**
 * This is the generic interface for a game. A game is a place where users
 * can interact with each other and with the game's logic. Typically the games
 * that players use are the <code>Lobby</code> or a <code>Dungeon</code>.
 */
public interface Game extends ManagedObject {

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
     * handle messages intended for this <code>Game</code>. 
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
