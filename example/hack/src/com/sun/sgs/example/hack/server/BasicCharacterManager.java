/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.share.Board;

import java.io.Serializable;


/**
 * This abstract implementation of <code>CharacterManager</code> provides
 * some of the common functionality for all managers, and is extended by
 * the managers for players and AIs.
 */
public abstract class BasicCharacterManager
    implements CharacterManager, Serializable {

    private static final long serialVersionUID = 1;

    // a reference to the current level
    private ManagedReference levelRef;

    // the position on the current level
    private int xPos;
    private int yPos;

    // a unique identifier for doing equality
    private String uniqueID;

    /**
     * Creates an instance of <code>BasicCharacterManager</code>.
     *
     * @param uniqueID a unique identifier for this instance
     */
    protected BasicCharacterManager(String uniqueID) {
        this.uniqueID = uniqueID;

        xPos = -1;
        yPos = -1;
    }

    /**
     * Returns the current level where this manager is playing.
     *
     * @return the current level
     */
    public Level getCurrentLevel() {
        if (levelRef == null)
            return null;
        return levelRef.get(Level.class);
    }

    /**
     * Sets the current level.
     *
     * @param levelRef a reference to a level
     */
    public void setCurrentLevel(Level level) {
        DataManager dataManager = AppContext.getDataManager();
        dataManager.markForUpdate(this);

        if (level == null)
            levelRef = null;
        else
            levelRef = dataManager.createReference(level);
    }

    /**
     * Sets the current character's position on the current level.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     */
    public void setLevelPosition(int x, int y) {
        AppContext.getDataManager().markForUpdate(this);

        xPos = x;
        yPos = y;
    }

    /**
     * Returns the current character's x-coordinate.
     *
     * @return the x-coordinate
     */
    public int getLevelXPos() {
        return xPos;
    }

    /**
     * Returns the current character's x-coordinate.
     *
     * @return the x-coordinate
     */
    public int getLevelYPos() {
        return yPos;
    }

    /**
     * Returns a unique representation of this manager.
     *
     * @return a <code>String</code> representation
     */
    public String toString() {
        return uniqueID;
    }

    /**
     * Compares another instance of <code>BasicCharacterManager</code>
     * against this one for equality.
     *
     * @param obj an instance of <code>BasicCharacterManager</code>
     *
     * @return true if the objects are equal, false otherwise
     */
    public boolean equals(Object obj) {
        if (! (obj instanceof CharacterManager))
            return false;

        return toString().equals(obj.toString());
    }

    /**
     * Returns a hash code for this manager, which is calculated by getting
     * the hash code on the value from <code>toString</code>.
     *
     * @return a hash code
     */
    public int hashCode() {
        return toString().hashCode();
    }

}
