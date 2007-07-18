/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.example.hack.server.Character;

import java.io.Serializable;


/**
 * This implementation of <code>Character</code> is the base for all AI
 * creatures (ie, Monsters and NPCs).
 */
public abstract class AICharacter implements Character, Serializable {

    private static final long serialVersionUID = 1;

    // the character's identifier
    private int id;

    // the character's name
    private String name;

    /**
     * Creates an instance of <code>AICharacter</code>.
     *
     * @param id the character's identifier
     * @param name the character's name
     */
    public AICharacter(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Returns this entity's identifier. Typically this maps to the sprite
     * used on the client-side to render this entity.
     *
     * @return the identifier
     */
    public int getID() {
        return id;
    }

    /**
     * Returns the name of this entity.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Called periodically to give this character a chance to do some
     * processing.
     */
    public abstract void run();

    /**
     * Resets the character's details and makes them ready to re-enter
     * a level. This typically happens after the character has been killed,
     * and it's being re-spawned.
     */
    public abstract void regenerate();

}
