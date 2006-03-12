
/*
 * PassableTile.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Mar  4, 2006	 9:23:05 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;

import com.sun.gi.apps.hack.server.CharacterManager;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;


/**
 * This implementation of <code>Tile</code> represents a space that any
 * single character may occupy.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class PassableTile extends BasicTile
{

    /**
     * Creates an instance of <code>PassableTile</code>
     *
     * @param id the tile's identifier
     */
    public PassableTile(int id) {
        super(id);
    }

    /**
     * Always returns true, since this space is always passable. Note that
     * this doesn't mean that the space is un-occupied, but it means that
     * its general behavior is that characters can occupy it.
     *
     * @param mgrRef the manager for a character
     */
    public boolean isPassable(GLOReference<? extends CharacterManager>
                              mgrRef) {
        return true;
    }

    /**
     * Test to move the given character onto this tile. If there is already
     * a character on this tile, then this method leads to the two
     * characters interacting.
     *
     * @param mgrRef the manager for a character
     */
    public ActionResult moveTo(CharacterManager mgr) {
        // moving to this tile when empty is always allowed, so we just need
        // to check if a character is here and would stop the movement
        return charMoveTo(mgr);
    }

}
