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

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.CharacterManager;


/**
 * A <code>Connector</code> is something that moves a <code>Character</code>
 * from one point in a game to another, or to another game. Typical examples
 * include stairs (which move you to the same point on another leve), and
 * the special <code>GameConnector</code> which moves you between a game
 * (typically the lobby) and dungeons.
 */
public interface Connector extends ManagedObject {

    /**
     * Transitions the given character from one point to another. This
     * may have well-defined behavior, always connecting two points (eg,
     * stairs on two connected levels) or providing one-way tunnels, or
     * it may be randomized.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(CharacterManager mgr);

}
