
/*
 * LevelBoard.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Mar  5, 2006	 2:07:55 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import com.sun.gi.apps.hack.share.Board;


/**
 * This is an extension to <code>Board</code> that is used to manage levels.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface LevelBoard extends Board
{

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
    public boolean addCharacterAt(int x, int y,
                                  GLOReference<? extends CharacterManager>
                                  mgrRef);

    /**
     * Tries to remove a character from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeCharacterAt(int x, int y,
                                     GLOReference<? extends CharacterManager>
                                     mgrRef);

    /**
     * Tries to add an item at the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addItemAt(int x, int y,
                             GLOReference<? extends Item> itemRef);

    /**
     * Tries to remove an item from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeItemAt(int x, int y,
                                GLOReference<? extends Item> itemRef);

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
    public boolean testMove(int x, int y,
                            GLOReference<? extends CharacterManager> mgrRef);

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
