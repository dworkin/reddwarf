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
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

//import com.sun.sgs.app.util.ScalableDeque;

import com.sun.sgs.example.hack.server.ai.CreatureCharacter;

import com.sun.sgs.example.hack.server.ai.logic.RandomWalkAILogic;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.server.util.Dice;

import com.sun.sgs.example.hack.share.CharacterStats;

import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureAttribute;

import java.io.Serializable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A periodic task that respawns creatures in LIFO order.  When a
 * creature perishes, they have the option of enqueuing themselves to
 * be respawned on the level that this respawner is associated with.
 * The creature that will be respawned is a new instance of the same
 * {@link CreatureType}, not the same creature with reset stats.  This
 * allows for some variability to the stats.  <i>Developer Note</i>:
 * later when items are fully supported, creating a new instance will
 * be important for item creation.
 *
 * @see CreatureCharacterManager
 * @see CreatureType#RESPAWNS
 */
public class CreatureRespawner
    implements ManagedObject, Serializable, Task {

    /**
     * The interval in milliseconds after which this class will
     * attempt respawn the longest deceased creature type.
     */
    private static final long DEFAULT_RESPAWN_INTERVAL = 5000;

    /**
     * A reference to the {@link Level} where this respawner will
     * place the creatures.
     */
    private final ManagedReference<Level> levelRef;

    // NOTE: Use a ScalableDeque after it has been added to a release
    //  private final ManagedReference<Deque<CreatureType>>
    // 	creaturesToBeRespawnedRef;
    
    /**
     * A queue of the of the creatures waiting to be respawned.  Once
     * per update interval, the head of this list is removed and a new
     * creature of that type is created
     */
    private final Queue<CreatureType> creaturesToBeRespawned;

    /**
     * Creature a new {@code CreatureRespawner} that will place
     * creatures on the provided {@code Level}
     *
     * @param level the level where the creatures will be placed
     */
    public CreatureRespawner(Level level) {
	DataManager dm = AppContext.getDataManager();
	levelRef = dm.createReference(level);
	// 	ScalableDeque<CreatureType> d = 
	// 	    new ScalableDeque<CreatureType>();
	// 	creaturesToBeRespawnedRef = dm.createReference(d);
	creaturesToBeRespawned = new ArrayDeque<CreatureType>();
	AppContext.getTaskManager().schedulePeriodicTask(
	    this, 0L, DEFAULT_RESPAWN_INTERVAL);
						 
    }
    
    /**
     * Adds a notice for a new creature of the provided {@code
     * CreatureType} to be respawned.  Creatures are respawned
     * according to a LIFO ordering, and this method makes no
     * guarantees about when the creature indicided by a method call
     * will be created.
     */ 
    public void addRespawn(CreatureType toRespawn) {
	// 	creaturesToBeRespawnedRef.get().offer(toRespawn);
	creaturesToBeRespawned.offer(toRespawn);
	AppContext.getDataManager().markForUpdate(this);
    }
    
    /**
     * Removes the next {@link CreatureType} to be respawned and adds
     * it to the {@link Level} associated with this respawner.
     */
    public void run() {
	// 	Deque<CreatureType> creaturesToBeRespawned =
	// 	    creaturesToBeRespawnedRef.get();
	
	CreatureType type = creaturesToBeRespawned.poll();
	if (type == null)
	    return;

	AppContext.getDataManager().markForUpdate(this);

	Level level = levelRef.get();

	// creatue a new creature of the type requested
	CreatureCharacterManager ccm = CreatureFactory.getCreature(type);
	ccm.setCreatureRespawner(this);

	CreatureCharacter character = ccm.getCurrentCharacter();

	// determine how fast the creature moves
	long movementRate = character.getMovementDelay();
	
	// first restart the creature's movement
	AppContext.getTaskManager().
	    schedulePeriodicTask(ccm, 0, movementRate);
	
	// then add the creature to the level
	level.addCharacter(ccm);
    }
}