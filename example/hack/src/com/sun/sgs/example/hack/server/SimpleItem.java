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

package com.sun.sgs.example.hack.server;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This is a simple implementation of <code>Item</code> that provides
 * non-interactive items.
 */
public class SimpleItem implements Item, Serializable {

    private static final long serialVersionUID = 1;

    // the identifier of this item
    private int id;

    /**
     * Creates an instance of <code>SimpleItem</code>.
     *
     * @param id the item's identifier
     */
    public SimpleItem(int id) {
        this.id = id;
    }

    /**
     * Returns the item's identifier.
     *
     * @return the identifier
     */
    public int getID() {
        return id;
    }

    /**
     * Called when this <code>Item</code> is being given to the character.
     * This method does nothing, since we're only supporting non-interactive
     * items in this class.
     */
    public ActionResult giveTo(CharacterManager characterManager) {
        return ActionResult.SUCCESS;
    }

}
