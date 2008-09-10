/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai.logic;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.server.ai.AICharacterManager;
import com.sun.sgs.example.hack.server.ai.CreatureCharacter;

import com.sun.sgs.example.hack.server.util.Dice;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureAttribute;
import com.sun.sgs.example.hack.share.KeyMessages;

import java.io.Serializable;

/**
 * The interface for controlling actions of {@code AICreature}
 * instances.  Each {@code AILogic} instance outlines a type behavoir
 * which can be used by multiple creature instances.  For example, a
 * random walk behavior simple moves randomly regardless of the
 * current situation.  An aggressive behavior might actively seek out
 * the character when given the opportunity.
 *
 * <p>
 * 
 * Creature details specify which instances of {@code AILogic} to
 * create to handle the behavior for instances of that type.  In this
 * way, creatures of the same {@link CreatureType} can use different
 * {@code AILogic} instances to not always exhibit the same behavior.
 */
public class RandomWalkAILogic implements AILogic, Serializable {

    public RandomWalkAILogic() { }

    /**
     * When an AI creature using this logic collides into another creature,
     * the AI creature will attack.
     */
    public void collidedInto(CreatureCharacter creature,
			     Character characterWeCollidedInto) {
	// always attack
	attack(creature, characterWeCollidedInto);
    }


    public void reactToCollisionFrom(CreatureCharacter creature, 
				     Character characterThatCollidedIntoUs) {
        // getting hit invokes a simple double-dispatch pattern, where
        // we call the other party and let them know that they hit us, and
        // then we react to this
	if (characterThatCollidedIntoUs.collidedInto(creature)) {
	    creature.notifyStatsChanged();
	}

	// we should check here to ensure the creature is still alive,
	// isn't paralyzed, etc.
	if (creature.getAttributes().contains(CreatureAttribute.RETALIATES)) {
	    attack(creature, characterThatCollidedIntoUs);
	}
    }

    protected void attack(CreatureCharacter attacker,
			  Character character) {
	// REMINDER: should we abstract this attack logic into another
	// class?  It might be useful to have all AILogic instances
	// call the same attack method so that things like critical,
	// elemental bonuses, etc. could be 

	int damageToDeal = attacker.getDamageDice().roll();

	CharacterStats theirStats = character.getStatistics();
        int newHp = (damageToDeal > theirStats.getHitPoints()) 
	    ? 0
            : theirStats.getHitPoints() - damageToDeal;
        theirStats.setHitPoints(newHp);

    }

    public void determineNextAction(CreatureCharacter creature,
				    Level currentLevel) {
	
	// get the level we're on now
	AICharacterManager mgr = creature.getManager();

	// pick a random direction, and try to move there
	switch ((int)(Math.random() * 4)) {
	case 0: currentLevel.move(mgr, KeyMessages.Type.UP);
	    break;
	case 1: currentLevel.move(mgr, KeyMessages.Type.DOWN);
	    break;
	case 2: currentLevel.move(mgr, KeyMessages.Type.LEFT);
	    break;
	case 4: currentLevel.move(mgr, KeyMessages.Type.RIGHT);
	    break;
        }	
    }

}