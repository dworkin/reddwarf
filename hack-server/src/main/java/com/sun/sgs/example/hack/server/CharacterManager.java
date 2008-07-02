/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.share.BoardSpace;

import com.sun.sgs.example.hack.share.Board;

import java.util.Collection;


/**
 * This interface defines all classes that manage {@link Character}
 * instances.  In essence, a <code>Character</code> represents a
 * single avitar in the game, but not the way that it's moved around
 * between elements of the world, the way in which it communicates
 * with its master (e.g., a player or some AI timing loop),
 * etc. Throughout the code, especially when we need to track
 * references, <code>CharacterManager</code>s are used to manage
 * <code>Character</code> interaction.
 */
public interface CharacterManager extends ManagedObject {

    /**
     * Returns the current character being played through this manager.
     *
     * @return the current character
     */
    public Character getCurrentCharacter();

    /**
     * Returns the current level where this manager is playing.
     *
     * @return the current level
     */
    public Level getCurrentLevel();

    /**
     * Sets the current level.
     *
     * @param level the current level
     */
    public void setCurrentLevel(Level level);

    /**
     * Sets the current character's position on the current level.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     */
    public void setLevelPosition(int x, int y);

    /**
     * Returns the current character's x-coordinate.
     *
     * @return the x-coordinate
     */
    public int getLevelXPos();

    /**
     * Returns the current character's x-coordinate.
     *
     * @return the x-coordinate
     */
    public int getLevelYPos();

    /**
     * Sends the given board to the backing controller (eg, a player) of
     * this manager.
     *
     * @param board the board to send
     */
    public void sendBoard(Board board);


    /**
     * Sends space updates to the backing controller (eg, a player) of
     * this manager.
     *
     * @param updates the updates to send
     */
    public void sendUpdate(Collection<BoardSpace> updates);

}
