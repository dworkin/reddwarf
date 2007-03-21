/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This implementation of <code>Tile</code> represents a space that no
 * character may pass, unless they override this behavior. An example of
 * this is a wall.
 */
public class ImpassableTile extends BasicTile implements Serializable {

    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of <code>ImpassableTile</code>
     *
     * @param id the tile's identifier
     */
    public ImpassableTile(int id) {
        super(id);
    }

    /**
     * Typically returns false, since this space is always impassable. Note
     * that this doesn't mean that no character may occupy it, only that
     * the default behavior is that you can't pass through this tile.
     *
     * @param mgrRef the manager for a character
     */
    public boolean isPassable(CharacterManager mgr) {
        // FIXME: we should check if the character overrides this behavior,
        // and if it does whether there's already a character here
        return false;
    }

    /**
     * Test to move the given character onto this tile. If there is already
     * a character on this tile, then this method leads to the two
     * characters interacting. Characters need to override default behavior
     * to be on this tile, so this generally returns <code>FAIL</code>.
     *
     * @param characterManager the manager for a character
     */
    public ActionResult moveTo(CharacterManager characterManager) {
        // FIXME: we should check if the character overrides this behavior,
        // and if it does whether there's already a character here
        return ActionResult.FAIL;
    }

}
