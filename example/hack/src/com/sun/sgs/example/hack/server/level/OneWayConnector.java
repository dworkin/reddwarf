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
package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.CharacterManager;

import java.io.Serializable;


/**
 * This is a <code>Connector</code> that transitions in only one direction.
 */
public class OneWayConnector implements Connector, Serializable {

    private static final long serialVersionUID = 1;

    // the target level
    private ManagedReference levelRef;

    // the target position
    private int xPos;
    private int yPos;

    /**
     * Creates an instance of <code>OneWayConnector</code>.
     *
     * @param levelRef a reference to a level that this connects to
     * @param xPos the x-coord on the level this connects to
     * @param yPos the y-coord on the level this connects to
     */
    public OneWayConnector(Level level, int xPos, int yPos) {
        levelRef = AppContext.getDataManager().createReference(level);

        this.xPos = xPos;
        this.yPos = yPos;
    }

    /**
     * Transitions the given character to the target point.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(CharacterManager mgr) {
        // this connector is easy...we just dump the player to the position
        levelRef.get(Level.class).addCharacter(mgr, xPos, yPos);

        return true;
    }

}
