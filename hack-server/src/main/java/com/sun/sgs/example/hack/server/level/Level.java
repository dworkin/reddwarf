/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.ServerItem;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.KeyMessages;


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
     * @param itemRef a reference to the <code>ServerItem</code>
     */
    public void addItem(ServerItem item);

    /**
     * Adds an item to this level at the given position.
     *
     * @param itemRef a reference to the <code>ServerItem</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     */
    public void addItem(ServerItem item, int startX, int startY);

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
    public boolean move(CharacterManager mgr, KeyMessages.Type direction);

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
