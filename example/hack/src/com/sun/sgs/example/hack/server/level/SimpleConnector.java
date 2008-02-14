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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.CharacterManager;

import java.io.Serializable;


/**
 * This implementation of <code>Connector</code> acts as a simple, two-way
 * connection between two fixed points. Those points may lie on any two
 * <code>Level<code>s, and may be the same <code>Level</code>.
 */
public class SimpleConnector implements Connector, Serializable {

    private static final long serialVersionUID = 1;

    // the two levels
    private ManagedReference level1Ref;
    private ManagedReference level2Ref;

    // the two sets of coordinates
    private int level1X;
    private int level2X;
    private int level1Y;
    private int level2Y;

    // an internal flag tracking whether the two levels are the same
    private boolean sameLevel;

    /**
     * Creates an instance of <code>SimpleConnector</code>.
     *
     * @param level1Ref a reference to a level
     * @param level1X the x-coord on the first level
     * @param level1Y the y-coord on the first level
     * @param level2Ref a reference to another level
     * @param level2X the x-coord on the second level
     * @param level2Y the y-coord on the second level
     */
    public SimpleConnector(Level level1, int level1X, int level1Y,
                           Level level2, int level2X, int level2Y) {
        DataManager dataManager = AppContext.getDataManager();
        level1Ref = dataManager.createReference(level1);
        level2Ref = dataManager.createReference(level2);

        this.level1X = level1X;
        this.level2X = level2X;
        this.level1Y = level1Y;
        this.level2Y = level2Y;

        // see if these are on the same level or not ... this isn't a
        // significant optimization, but it helps clarify things in
        // the enteredConnection method
        if (level1Ref.equals(level2Ref))
            sameLevel = true;
        else
            sameLevel = false;
    }

    /**
     * Transitions the given character to the other point connected to
     * their current location.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(CharacterManager mgr) {
        handleEntered(mgr);

        return true;
    }

    /**
     * Figures out which end to send the character to, based on which end
     * they're on right now, and moves the character.
     *
     * @param mgrRef a reference to the character's manager
     */
    protected void handleEntered(CharacterManager mgr) {
        // see if we can use the level ref info
        if (sameLevel) {
            // we make a connection on the same level, so use position
            // information to figure out which direction we're going in
            if ((mgr.getLevelXPos() == level1X) &&
                (mgr.getLevelYPos() == level1Y)) {
                // we're on level1, moving to level2
                level2Ref.get(Level.class).addCharacter(mgr, level2X, level2Y);
            } else {
                // we're on level2, moving to level1
                level1Ref.get(Level.class).addCharacter(mgr, level1X, level1Y);
            }
        } else {
            // we connect different levels, so look at the level where the
            // character is now
            ManagedReference levelRef =
                AppContext.getDataManager().
                createReference(mgr.getCurrentLevel());
            if (levelRef.equals(level1Ref)) {
                // we're moving to level2
                level2Ref.get(Level.class).addCharacter(mgr, level2X, level2Y);
            } else {
                // we're moving to level1
                level1Ref.get(Level.class).addCharacter(mgr, level1X, level1Y);
            }
        }
    }

}
