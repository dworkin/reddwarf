/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.share.CharacterStats;


/**
 * This abstract implementation of <code>AICharacter</code> is the base
 * for all Monsters.
 */
public abstract class MonsterCharacter extends AICharacter {

    // a self-reference
    private ManagedReference mgrRef;

    /**
     * Creates an instance of <code>MonsterCharacter</code>.
     *
     * @param id the identifier for this character
     * @param name the name for this character
     */
    protected MonsterCharacter(int id, String name, AICharacterManager mgr) {
        super(id, name);

        mgrRef = AppContext.getDataManager().createReference(mgr);
    }

    /**
     * Returns the character manager for this character.
     */
    protected AICharacterManager getManager() {
        return mgrRef.get(AICharacterManager.class);
    }

    /**
     * This method tends to have common behavior, so a default implementation
     * is provided here. This checks to see if the character's hit points
     * are now at zero, and if they are it does the death notification and
     * removes the character from the level.
     */
    public void notifyStatsChanged() {
        // if we have no hit points left, we were killed in battle
        if (getStatistics().getHitPoints() == 0) {
            AICharacterManager mgr = mgrRef.get(AICharacterManager.class);
            mgr.getCurrentLevel().removeCharacter(mgr);
            mgr.notifyCharacterDied();
        }
    }

}
