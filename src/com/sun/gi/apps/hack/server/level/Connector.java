
/*
 * Connector.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Mar  3, 2006	 9:40:32 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;

import com.sun.gi.apps.hack.server.CharacterManager;


/**
 * A <code>Connector</code> is something that moves a <code>Character</code>
 * from one point in a game to another, or to another game. Typical examples
 * include stairs (which move you to the same point on another leve), and
 * the special <code>GameConnector</code> which moves you between a game
 * (typically the lobby) and dungeons.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Connector extends GLO
{

    /**
     * Transitions the given character from one point to another. This
     * may have well-defined behavior, always connecting two points (eg,
     * stairs on two connected levels) or providing one-way tunnels, or
     * it may be randomized.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(GLOReference<? extends CharacterManager>
                                     mgrRef);

}
