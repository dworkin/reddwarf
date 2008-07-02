/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.CharacterManager;


/**
 * A <code>Connector</code> is something that moves a <code>Character</code>
 * from one point in a game to another, or to another game. Typical examples
 * include stairs (which move you to the same point on another leve), and
 * the special <code>GameConnector</code> which moves you between a game
 * (typically the lobby) and dungeons.
 */
public interface Connector extends ManagedObject {

    /**
     * Transitions the given character from one point to another. This
     * may have well-defined behavior, always connecting two points (eg,
     * stairs on two connected levels) or providing one-way tunnels, or
     * it may be randomized.
     *
     * @param mgr the character's manager
     */
    public boolean enteredConnection(CharacterManager mgr);

}
