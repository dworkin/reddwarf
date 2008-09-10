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

import com.sun.sgs.example.hack.server.ai.CreatureCharacter;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;

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
public interface AILogic {

    void reactToCollisionFrom(CreatureCharacter creature, 
			     Character characterThatCollidedIntoUs);

    void determineNextAction(CreatureCharacter creature,
			     Level currentLevel);

    void collidedInto(CreatureCharacter creature,
		      Character chracterWeCollidedInto);

}