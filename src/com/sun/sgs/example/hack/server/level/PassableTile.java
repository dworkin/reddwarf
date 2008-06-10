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
