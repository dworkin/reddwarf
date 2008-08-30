/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.ServerItem;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.RoomInfo.FloorType;

import java.io.Serializable;


/**
 * This interface defines a single square on a board. It is used to
 * maintain internal state about what's in a level. Instances of this
 * interface are not a {@link ManagedObject}s because they are
 * included as private state for other <code>ManagedObject</code>s.
 */
public interface Tile extends Serializable, ManagedObject {

    /**
     * Returns the identifier for this tile. Typically this is the sprite
     * identifier for this space on the board.
     *
     * @return the tile's identifier
     */
    //public int getID();
    
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
    //public BoardSpace getBoardSpace();

    public FloorType getFloorType();

    /**
     * Returns whether or not this space could be entered by the given
     * character. Note that this only tests if the tile itself is passable.
     * If there is a character or item on this space that would block
     * access, it will still return true. If you want to test for the ability
     * to actually move to the space in its current state, use
     * <code>canOccupy</code>.
     *
     * @param mgr the manager for a character
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
     * @param mgr the manager for a character
     *
     * @return whether or not the character can occupy this tile
     */
    public boolean canOccupy(CharacterManager mgr);

    /**
     * Adds the given character to this tile if possible.
     *
     * @param mgr the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(CharacterManager mgr);

    /**
     * Removes the given character from this tile, if and only if this
     * character is already on this tile.
     *
     * @param mgr the manager for a character
     *
     * @return whether or not the character was removed successfully
     */
    public boolean removeCharacter(CharacterManager mgrRef);

    /**
     * Adds the given item to this tile if possible.
     *
     * @param item the manager for the item
     *
     * @return whether or not the item was added successfully
     */
    public boolean addItem(ServerItem item);

    /**
     * Removes the given item from this tile, if and only if this item
     * is already on this tile.
     *
     * @param item the manager for the item
     *
     * @return whether or not the item was removed successfully
     */
    public boolean removeItem(ServerItem item);

    public ServerItem getItem();

    public CharacterManager getCharacterManager();
    

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
