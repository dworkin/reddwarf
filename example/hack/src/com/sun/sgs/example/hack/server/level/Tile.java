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

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.Item;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This interface defines a single square on a board. It is used to maintain
 * internal state about what's in a level. Its is not a <code>GLO</code>
 * because it is private state for <code>GLO</code>s.
 */
public interface Tile extends Serializable {

    /**
     * Returns the identifier for this tile. Typically this is the sprite
     * identifier for this space on the board.
     *
     * @return the tile's identifier
     */
    public int getID();

    /**
     * Returns a stack of identifiers, specifying everything on this
     * <code>Tile</code>. The the zeroeith index is always the same value
     * as calling <code>getID</code>. If there is am item on this tile,
     * it is in the next index, and if there is a character on this
     * tile, it always appears last. There may be multiple items or
     * characters on a tile, depending on implementation.
     *
     * @return the set of identifiers for the things at this space
     */
    public int [] getIdStack();

    /**
     * Returns whether or not this space could be entered by the given
     * character. Note that this only tests if the tile itself is passable.
     * If there is a character or item on this space that would block
     * access, it will still return true. If you want to test for the ability
     * to actually move to the space in its current state, use
     * <code>canOccupy</code>.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the tile is passable
     */
    public boolean isPassable(CharacterManager mgr);

    /**
     * Returns whether or not this tile, in its current state, can be
     * occupied by the given character. This takes into account the
     * passability of the tile as well as anything that might currently
     * occupy it. If you just want a simple test for tile passability,
     * use <code>isPassable</code>.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character can occupy this tile
     */
    public boolean canOccupy(CharacterManager mgr);

    /**
     * Adds the given character to this tile if possible.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(CharacterManager mgr);

    /**
     * Removes the given character from this tile, if and only if this
     * character is already on this tile.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was removed successfully
     */
    public boolean removeCharacter(CharacterManager mgrRef);

    /**
     * Adds the given item to this tile if possible.
     *
     * @param itemRef the manager for the item
     *
     * @return whether or not the item was added successfully
     */
    public boolean addItem(Item item);

    /**
     * Removes the given item from this tile, if and only if this item
     * is already on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return whether or not the item was removed successfully
     */
    public boolean removeItem(Item item);

    /**
     * Test to move the given character to this tile. Note that this does
     * not actually move the character, since doing so requires knowledge
     * of the other tiles with which the character is interacting. This
     * method does test that the move can be done, and does handle any
     * interactions like moving the character to a new level or attacking
     * another character.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of making the move
     */
    public ActionResult moveTo(CharacterManager characterManager);

    /**
     * Tries to take an item on this tile. Unlike <code>moveTo</code>,
     * this actually will remove items from the tile if they are
     * successfully taken.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of getting an item
     */
    public ActionResult getItem(CharacterManager characterManager);

}
