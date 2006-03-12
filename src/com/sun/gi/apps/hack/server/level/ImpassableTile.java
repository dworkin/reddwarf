
/*
 * ImpassableTile.java
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
 * This implementation of <code>Tile</code> represents a space that no
 * character may pass, unless they override this behavior. An example of
 * this is a wall.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class ImpassableTile extends BasicTile
{

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
    public boolean isPassable(GLOReference<? extends CharacterManager>
                              mgrRef) {
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
     * @param mgrRef the manager for a character
     */
    public ActionResult moveTo(CharacterManager characterManager) {
        // FIXME: we should check if the character overrides this behavior,
        // and if it does whether there's already a character here
        return ActionResult.FAIL;
    }

}
