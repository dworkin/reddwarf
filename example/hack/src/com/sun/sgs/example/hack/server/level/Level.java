/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.Item;

import com.sun.sgs.example.hack.share.Board;


/**
 * This interface represents a single level in a <code>Dungeon</code>.
 */
public interface Level extends ManagedObject {

    /**
     * The standard name prefix for all levels.
     */
    public static final String NAME_PREFIX = "level:";

    /**
     * Returns the name of this level.
     *
     * @return the name
     */
    public String getName();

    /**
     * Adds a character to this level at some random point.
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     */
    public void addCharacter(CharacterManager mgr);

    /**
     * Adds a character to this level at the given location.
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     * 
     * @return true upon success, otherwise false
     */
    public boolean addCharacter(CharacterManager mgr, int startX, int startY);

    /**
     * Removes a character from the level. This is typically only called
     * when a character wants to remove itself directly (eg, they were
     * killed, or quit back to the lobby). Otherwise, characters are
     * removed naturally through other actions (like movement).
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     */
    public void removeCharacter(CharacterManager mgr);

    /**
     * Adds an item to this level at some random position.
     *
     * @param itemRef a reference to the <code>Item</code>
     */
    public void addItem(Item item);

    /**
     * Adds an item to this level at the given position.
     *
     * @param itemRef a reference to the <code>Item</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     */
    public void addItem(Item item, int startX, int startY);

    /**
     * Returns a snapshot (ie, static) view of the level.
     *
     * @return a snapshot of the board
     */
    public Board getBoardSnapshot();

    /**
     * Tries to move the given character in the given direction
     *
     * @param mgr the manager for the <code>Character</code> that is trying
     *            to move
     * @param direction the direction in which the character wants to move
     *
     * @return true if we moved in the requested direction, false otherwise
     */
    public boolean move(CharacterManager mgr, int direction);

    /**
     * Tries to take items at the character's current location.
     *
     * @param mgr the manager for the <code>Character</code> that is trying
     *            to take the items
     *
     * @return true if we took something, false otherwise
     */
    public boolean take(CharacterManager mgr);

}
