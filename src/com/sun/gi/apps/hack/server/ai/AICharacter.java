
/*
 * AICharacter.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Mar  5, 2006	 2:58:01 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.ai;

import com.sun.gi.apps.hack.server.Character;


/**
 * This implementation of <code>Character</code> is the base for all AI
 * creatures (ie, Monsters and NPCs).
 *
 * @since 1.0
 * @author Seth Proctor
 */
public abstract class AICharacter implements Character
{

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
