/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
