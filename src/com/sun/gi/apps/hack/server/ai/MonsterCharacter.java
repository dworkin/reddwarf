
/*
 * MonsterCharacter.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Tue Mar  7, 2006	 3:14:12 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.ai;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import com.sun.gi.apps.hack.server.Character;
import com.sun.gi.apps.hack.server.CharacterManager;

import com.sun.gi.apps.hack.share.CharacterStats;


/**
 * This abstract implementation of <code>AICharacter</code> is the base
 * for all Monsters.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public abstract class MonsterCharacter extends AICharacter
{

    // a self-reference
    private GLOReference<AICharacterManager> mgrRef;

    /**
     * Creates an instance of <code>MonsterCharacter</code>.
     *
     * @param id the identifier for this character
     * @param name the name for this character
     */
    protected MonsterCharacter(int id, String name,
                               GLOReference<AICharacterManager> mgrRef) {
        super(id, name);

        this.mgrRef = mgrRef;
    }

    /**
     * Returns the character manager for this character.
     */
    protected GLOReference<AICharacterManager> getManagerRef() {
        return mgrRef;
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
            SimTask task = SimTask.getCurrent();
            AICharacterManager mgr = mgrRef.get(task);
            mgr.getCurrentLevel().get(task).removeCharacter(mgrRef);
            mgr.notifyCharacterDied();
        }
    }

}
