/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.example.hack.server.Character;

import java.io.Serializable;


/**
 * This implementation of <code>Character</code> is the base for all AI
 * creatures (ie, Monsters and NPCs).
 */
public abstract class AICharacter implements Character, Serializable {

    private static final long serialVersionUID = 1;

    // the character's identifier
    private int id;

    // the character's name
    private String name;

    /**
     * Creates an instance of <code>AICharacter</code>.
     *
     * @param id the character's identifier
     * @param name the character's name
     */
    public AICharacter(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Returns this entity's identifier. Typically this maps to the sprite
     * used on the client-side to render this entity.
     *
     * @return the identifier
     */
    public int getID() {
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

    /**
     * Resets the character's details and makes them ready to re-enter
     * a level. This typically happens after the character has been killed,
     * and it's being re-spawned.
     */
    public abstract void regenerate();

}
