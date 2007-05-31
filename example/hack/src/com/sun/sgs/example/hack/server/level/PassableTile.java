/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This implementation of <code>Tile</code> represents a space that any
 * single character may occupy.
 */
public class PassableTile extends BasicTile implements Serializable {

    private static final long serialVersionUID = 1;

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
     *
     * @return true
     */
    public boolean isPassable(CharacterManager mgr) {
        return true;
    }

    /**
     * Test to move the given character onto this tile. If there is already
     * a character on this tile, then this method leads to the two
     * characters interacting.
     *
     * @param mgr the manager for a character
     */
    public ActionResult moveTo(CharacterManager mgr) {
        // moving to this tile when empty is always allowed, so we just need
        // to check if a character is here and would stop the movement
        return charMoveTo(mgr);
    }

}
