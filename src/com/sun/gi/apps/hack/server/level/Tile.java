
/*
 * Tile.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Mar  4, 2006	 9:20:53 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This interface defines a single square on a board. It is used to maintain
 * internal state about what's in a level. Its is not a <code>GLO</code>
 * because it is private state for <code>GLO</code>s.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Tile extends Serializable
{

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
     * Returns whether or not this space can be entered by the given
     * character.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character can move to this tile
     */
    public boolean isPassable(GLOReference<? extends CharacterManager> mgrRef);

    /**
     * Adds the given character to this tile if possible.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(GLOReference<? extends CharacterManager>
                                mgrRef);

    /**
     * Removes the given character from this tile, if and only if this
     * character is already on this tile.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was removed successfully
     */
    public boolean removeCharacter(GLOReference<? extends CharacterManager>
                                   mgrRef);

    /**
     * Adds the given item to this tile if possible.
     *
     * @param itemREf the manager for the item
     *
     * @return whether or not the item was added successfully
     */
    public boolean addItem(GLOReference<? extends Item> itemRef);

    /**
     * Removes the given item from this tile, if and only if this item
     * is already on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return whether or not the item was removed successfully
     */
    public boolean removeItem(GLOReference<? extends Item> mgrRef);

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
