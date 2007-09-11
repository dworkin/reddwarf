/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.Item;

import com.sun.sgs.example.hack.share.Board;


/**
 * This is an extension to <code>Board</code> that is used to manage levels.
 */
public interface LevelBoard extends Board {

    /**
     * The possible results of taking an action on this board.
     */
    public enum ActionResult { SUCCESS, FAIL, CHARACTER_LEFT }

    /**
     * Tries to add a character at the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addCharacterAt(int x, int y, CharacterManager mgr);

    /**
     * Tries to remove a character from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeCharacterAt(int x, int y, CharacterManager mgr);

    /**
     * Tries to add an item at the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param itemRef a reference to the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addItemAt(int x, int y, Item item);

    /**
     * Tries to remove an item from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param itemRef a reference to the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeItemAt(int x, int y, Item item);

    /**
     * Tests to see if a move would be possible to the given location for
     * the given character.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation would succeed, false otherwise
     */
    public boolean testMove(int x, int y, CharacterManager mgr);

    /**
     * Moves the given character to the given location. The character must
     * alredy be on the board through a call to <code>addCharacterAt</code>.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgr the character's manager
     *
     * @return the result of attempting the move
     */
    public ActionResult moveTo(int x, int y, CharacterManager mgr);

    /**
     * Gets the items available at the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgr the character's manager
     *
     * @return the result of attempting to get the items
     */
    public ActionResult getItem(int x, int y, CharacterManager mgr);

}
