/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(CharacterManager mgr);

}
