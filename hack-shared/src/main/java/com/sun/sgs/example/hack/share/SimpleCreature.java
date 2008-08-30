/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;

import com.sun.sgs.example.hack.share.CreatureInfo.Creature;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;

import java.io.Serializable;


/**
 *
 */
public class SimpleCreature implements Creature, Serializable {

    private final String name;

    private final long id;

    private final CreatureType type;

    public SimpleCreature(CreatureType type, long id, String name) {
	this.type = type;
	this.id = id;
	this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public long getCreatureId() {
	return id;
    }
    
    /**
     * {@inheritDoc}
     */
    public CreatureType getCreatureType() {
	return type;
    }
    
    /**
     * {@inheritDoc}
     */
    public String getName() {
	return name;
    }


}
