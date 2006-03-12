
/*
 * CharacterManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Mar  3, 2006	 9:44:27 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;

import com.sun.gi.apps.hack.server.level.Level;

import com.sun.gi.apps.hack.share.BoardSpace;

import com.sun.gi.apps.hack.share.Board;

import java.util.Collection;


/**
 * This interface defines all classes that manage <code>Character</code>s.
 * In essence, a <code>Character</code> represents a single avitar in the
 * game, but not the way that it's moved around between elements of the
 * world, the way in which it communicates with its master (eg, a player
 * or some AI timing loop), etc. Throughout the code, especially when
 * we need to track references, <code>CharacterManager</code>s are used to
 * manage <code>Character</code> interaction.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface CharacterManager extends GLO
{

    /**
     * Returns a reference to this manager.
     *
     * @return a self-reference
     */
    public GLOReference<? extends CharacterManager> getReference();

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
    public GLOReference<? extends Level> getCurrentLevel();

    /**
     * Sets the current level.
     *
     * @param levelRef a reference to a level
     */
    public void setCurrentLevel(GLOReference<? extends Level> levelRef);

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
