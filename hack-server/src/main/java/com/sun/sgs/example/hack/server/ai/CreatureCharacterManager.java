/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.share.CreatureInfo.CreatureAttribute;

/**
 * This implementation of CharacterManager is used for all AI
 * creatures. It adds the ability to do regular invocations of AI
 * characters and handle their death and re-generation.
 */
public class CreatureCharacterManager extends AICharacterManager {
    
    /**
     * A reference to the {@code CreatureRespawner} that will be used
     * to respawn this creature if it has the {@link
     * CreatureAttribute#RESPAWNS} attribute.
     */
    private ManagedReference<CreatureRespawner> respawnerRef;

    public CreatureCharacterManager() {
	respawnerRef = null;
    }

    public CreatureCharacter getCurrentCharacter() {
	return (CreatureCharacter)(super.getCurrentCharacter());
    }
    
    /**
     * Notify the manager that its creature has died, which removes
     * the creature from the level and optionally respawns a new
     * creature of the same type if this creature had the {@link
     * CreatureAttribute#RESPAWNS} attribute.
     */
    public void notifyCharacterDied() {
	// stop the character and remove it from the board
	super.notifyCharacterDied();

	CreatureCharacter creature = getCurrentCharacter();	
	// if the creature supports respawning, notify the respawner
	// that it should add a new creature at some point in the
	// future
	if (creature.getAttributes().contains(CreatureAttribute.RESPAWNS) &&
	    respawnerRef != null)
	    respawnerRef.get().addRespawn(creature.getCreatureType());
    }

    /**
     *
     * @throws IllegalArgumentException if {@code character} is not an
     *         instance of {@link CreatureCharacter}
     */
    public void setCharacter(AICharacter character) {
	if (!(character instanceof CreatureCharacter))
	    throw new IllegalArgumentException(
		"character must be of type CreatureCharacter" + character);
	super.setCharacter(character);
    }

    /**
     * Sets the creature respawner that this manager should use if the
     * associated creature has died.
     */
    public void setCreatureRespawner(CreatureRespawner respawner) {
	respawnerRef = AppContext.getDataManager().createReference(respawner);
    }
}