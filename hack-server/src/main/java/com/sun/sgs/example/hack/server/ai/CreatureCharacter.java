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

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.server.ai.logic.AILogic;

import com.sun.sgs.example.hack.server.level.Level;
import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.server.util.Dice;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureAttribute;

import java.util.Set;

/**
 * This abstract implementation of {@code AICharacter} is the
 * container for all creature instances.
 *
 * <p>
 *
 * TODO: details about creation, etc.
 *
 * @see NPCharacter
 */
public class CreatureCharacter extends AICharacter implements ServerCreature {

    /**
     * A reference to the {@code AICharacterManger} that handles all
     * the server-communication logic. 
     */
    private final ManagedReference<AICharacterManager> mgrRef;

    private final CharacterStats stats;

    private final AILogic logic;

    private final int alertness;

    private final int movementDelay;

    private final int sightAbility;

    private final Dice damageDice;

    private final Set<CreatureAttribute> attributes;
    
    /**
     * Creates an instance of <code>MonsterCharacter</code>.
     *
     * @param id the identifier for this character
     * @param name the name for this character
     */
    protected CreatureCharacter(AICharacterManager mgr,
				AILogic logic,
				CreatureType creatureType, 
				String name,
				CharacterStats stats,
				Dice damageDice,
				int alertness,
				int sightAbility,
				int movementDelay,
				Set<CreatureAttribute> attributes) {
        super(creatureType, name);
	this.logic = logic;
	this.stats = stats;
	this.damageDice = damageDice;
	this.alertness = alertness;
	this.sightAbility = sightAbility;
	this.movementDelay = movementDelay;
	this.attributes = attributes;
        mgrRef = AppContext.getDataManager().createReference(mgr);
    }


    /**
     * Called when a character collides into us. There is some chance that
     * we will retaliate.
     *
     * @param character the character that hit us
     *
     * @return <code>ActionResult.FAIL</code>
     */
    public ActionResult collidedFrom(Character character) {
        // let them interact with us first
//         if (character.collidedInto(this))
//             notifyStatsChanged();

        // if we're still alive, there's a small chance that we'll strike
        // back at the attacker
//         if(stats.getHitPoints() > 0) {
//             if (NSidedDie.roll8Sided() == 8) {
//                 attack(character);
//                 character.notifyStatsChanged();
//             }
//         }

	// call the AI logic to decide what to do when the provided
	// character collides into us
	logic.reactToCollisionFrom(this, character);

	// return FAIL since the character cannot move into our
	// square.  However, we could change this so that if this AI
	// creature is killed, the colliding character moves into this
	// location
        return ActionResult.FAIL;
    }

    /**
     * Called when you collide with the character.
     *
     * @param character the character we hit
     *
     * @return boolean if any statistics changed
     */
    public boolean collidedInto(Character character) {
        // attack(character);
	
	// call the AI logic to decide what to do now that we've
	// collided into another character
	logic.collidedInto(this, character);
	
        return true;
    }

    public int getAlertness() {
	return alertness;
    }
    
    public Set<CreatureAttribute> getAttributes() {
	return attributes;
    }

    public long getCreatureId() {
	return getCharacterId();
    }

    public Dice getDamageDice() {
	return damageDice;
    }
    
    /**
     * Returns the character manager for this character.
     */
    public AICharacterManager getManager() {
        return mgrRef.get();
    }

    public int getMovementDelay() {
	return movementDelay;
    }

    public int getSightAbility() {
	return sightAbility;
    }

    /**
     * Returns the statistics associated with this character.
     *
     * @return the character's statistics
     */
     public CharacterStats getStatistics() {
         return stats;
     }

    /**
     * There is a good chance that we'll move 
     */
    public void run() {
        // there's a 5-in-6 chance that we'll decide to move
//         if (NSidedDie.roll6Sided() != 6) {
//             // get the level we're on now
//             AICharacterManager mgr = getManager();
//             Level level = mgr.getCurrentLevel();

//             // pick a direction, and try to move in that direction
//             switch (NSidedDie.roll4Sided()) {
//             case 1: level.move(mgr, KeyMessages.Type.UP);
//                 break;
//             case 2: level.move(mgr, KeyMessages.Type.DOWN);
//                 break;
//             case 3: level.move(mgr, KeyMessages.Type.LEFT);
//                 break;
//             case 4: level.move(mgr, KeyMessages.Type.RIGHT);
//                 break;
//             }
//         }

	// call the AI logic to make a move based on the current state
	// of the creature
	logic.determineNextAction(this, mgrRef.get().getCurrentLevel());
    }

    public void sendMessage(String messageFromServer) {
	// no-op
    }
    
    public void notifyStatsChanged() {
	checkIfAlive();
    }

    /**
     * This method tends to have common behavior, so a default implementation
     * is provided here. This checks to see if the character's hit points
     * are now at zero, and if they are it does the death notification and
     * removes the character from the level.
     *
     * @return {@code true} if this creature is still alive.
     */
    boolean checkIfAlive() {
        // if we have no hit points left, we were killed in battle
        if (getStatistics().getHitPoints() <= 0) {
	    AICharacterManager mgr = mgrRef.get();
 	    Level level = mgr.getCurrentLevel();
 	    if (level != null) {
 		mgr.notifyCharacterDied();
 	    }
	    return false;
        }
	return true;
    }

}
