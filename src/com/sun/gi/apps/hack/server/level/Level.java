
/*
 * Level.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Mar  2, 2006	 9:22:16 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import com.sun.gi.apps.hack.share.Board;


/**
 * This interface represents a single level in a <code>Dungeon</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Level extends GLO
{

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
    public void addCharacter(GLOReference<? extends CharacterManager> mgrRef);

    /**
     * Adds a character to this level at the given location.
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     */
    public void addCharacter(GLOReference<? extends CharacterManager> mgrRef,
                             int startX, int startY);

    /**
     * Removes a character from the level. This is typically only called
     * when a character wants to remove itself directly (eg, they were
     * killed, or quit back to the lobby). Otherwise, characters are
     * removed naturally through other actions (like movement).
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     */
    public void removeCharacter(GLOReference<? extends CharacterManager>
                                mgrRef);

    /**
     * Adds an item to this level at some random position.
     *
     * @param itemRef a reference to the <code>Item</code>
     */
    public void addItem(GLOReference<? extends Item> itemRef);

    /**
     * Adds an item to this level at the given position.
     *
     * @param itemRef a reference to the <code>Item</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     */
    public void addItem(GLOReference<? extends Item> itemRef, int startX,
                        int startY);

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
    public boolean move(CharacterManager mg, int direction);

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
