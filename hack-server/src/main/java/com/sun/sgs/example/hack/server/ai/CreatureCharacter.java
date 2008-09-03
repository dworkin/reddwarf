/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;

/**
 * This abstract implementation of <code>AICharacter</code> is the base
 * for all Monsters.
 */
public abstract class MonsterCharacter extends AICharacter {

    // a self-reference
    private ManagedReference<AICharacterManager> mgrRef;

    /**
     * Creates an instance of <code>MonsterCharacter</code>.
     *
     * @param id the identifier for this character
     * @param name the name for this character
     */
    protected MonsterCharacter(CreatureType creatureType, String name,
			       AICharacterManager mgr) {
        super(creatureType, name);

        mgrRef = AppContext.getDataManager().createReference(mgr);
    }

    /**
     * Returns the character manager for this character.
     */
    protected AICharacterManager getManager() {
        return mgrRef.get();
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
            AICharacterManager mgr = mgrRef.get();
            mgr.getCurrentLevel().removeCharacter(mgr);
            mgr.notifyCharacterDied();
        }
    }

}
