/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.Character;

import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;

import java.io.Serializable;


/**
 * This implementation of {@code Character} is the base for all AI
 * creatures (ie, Creature and NPCs).
 *
 * @see CreatureCharacter
 * @see NPCharacter
 */
public abstract class AICharacter 
    implements Character, ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * The character's creature type
     */
    private final CreatureType creatureType;

    /**
     * The character's name
     */
    private final String name;
    
    /**
     * The unique Id of this instance
     */
    private final long id;

    /**
     * Creates an instance of <code>AICharacter</code>.
     *
     * @param id the character's identifier
     * @param name the character's name
     */
    public AICharacter(CreatureType creatureType, String name) {
        this.creatureType = creatureType;
        this.name = name;
	this.id = AppContext.getDataManager().createReference(this).
	    getId().longValue();
    }

    /**
     * {@inheritDoc}
     */
    public CreatureType getCreatureType() {
	return creatureType;
    }

    /**
     * Returns this entity's unqiue identifier.  This can be used to
     * distinguish this entity from other entities with the same type
     * and name.
     *
     * @return the identifier
     */
    public long getCharacterId() {
        return id;
    }

    /**
     * Returns the name of this entity.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Called periodically to give this character a chance to do some
     * processing.
     */
    public abstract void run();

}
