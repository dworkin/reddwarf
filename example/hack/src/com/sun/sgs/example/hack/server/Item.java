/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
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

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;


/**
 * This is the interface for all items in the game.
 */
public interface Item extends ManagedObject {

    /**
     * Returns the item's identifier.
     *
     * @return the identifier
     */
    public int getID();

    /**
     * Called when this <code>Item</code> is being given to the character.
     * This is useful if you want interactive items (eg, cursing the user
     * as soon as they pickup a talisman).
     */
    public ActionResult giveTo(CharacterManager characterManager);

}
