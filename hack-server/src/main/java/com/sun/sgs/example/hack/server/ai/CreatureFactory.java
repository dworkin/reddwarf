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

import com.sun.sgs.example.hack.server.ai.CreatureCharacter;

import com.sun.sgs.example.hack.server.ai.logic.RandomWalkAILogic;

import com.sun.sgs.example.hack.server.util.Dice;

import com.sun.sgs.example.hack.share.CharacterStats;

import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureAttribute;

import java.util.EnumSet;

/**
 * This class is a prototype example of a pluggable object creator; so
 * you can easily introduce new kinds of monsters and reference them,
 * but for now just making sure that we use a factory gives us that
 * flexability in the future.
 *
 * Note that the implementation of this class is rudamentary and is
 * provided as an example of where to start in future implementations
 * or revisions.
 */
public class CreatureFactory {

    /**
     * Creates an instance of {@code AICharacterManager} and returns a
     * reference to the new instance
     *
     * @param type the creatures's type
     *
     * @throws IllegalArgumentException if {@code type} is not a
     *         recognized character type.
     */
    public static CreatureCharacterManager 
	getCreature(CreatureType type) {
        // create a manager
        CreatureCharacter character = null;

        CreatureCharacterManager charMgr =
            new CreatureCharacterManager();
	
        // figute out what kind of monster we're creating
	switch (type) {
	case DEMON: {
	    CharacterStats stats = 
		new CharacterStats("Demon", 20, 10, 15, 15, 15, 18, 50, 50);
	    
	    character = 
		new CreatureCharacter(charMgr,
				      new RandomWalkAILogic(),
				      CreatureType.DEMON,
				      "Demon",
				      stats,
				      new Dice(2, 5),
				      10, // alertness
				      5,  // sight ability
				      250,
				      EnumSet.of(CreatureAttribute.RESPAWNS));
	    break;
	}
	case RODENT: {
	    CharacterStats stats = 
		new CharacterStats("Rodent", 20, 10, 15, 15, 15, 18, 10, 10);

            character = 		
		new CreatureCharacter(charMgr,
				      new RandomWalkAILogic(),
				      CreatureType.RODENT,
				      "Rodent",
				      stats,
				      new Dice(1, 3),
				      1, // alertness
				      1, // sight ability
				      100,
				      EnumSet.of(CreatureAttribute.RESPAWNS));
	    break;
	}
	default:
            DataManager dataManager = AppContext.getDataManager();
            dataManager.removeBinding(charMgr.toString());
            dataManager.removeObject(charMgr);
            throw new IllegalArgumentException("Unknown monster type: " +
                                               type);
        }

	charMgr.setCharacter(character);
       
        return charMgr;
    }

}
