/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.PlayerCharacterManager;

import java.io.Serializable;


/**
 * This is an extension of <code>SimpleConnector</code> that only allows
 * <code>PlayerCharacter</code>s to enter. This lets you create boundries
 * for AI creatures.
 */
public class PlayerConnector extends SimpleConnector implements Serializable {

    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of <code>PlayerConnector</code>.
     *
     * @param level1Ref a reference to a level
     * @param level1X the x-coord on the first level
     * @param level1Y the y-coord on the first level
     * @param level2Ref a reference to another level
     * @param level2X the x-coord on the second level
     * @param level2Y the y-coord on the second level
     */
    public PlayerConnector(Level level1, int level1X, int level1Y,
                           Level level2, int level2X, int level2Y) {
        super(level1, level1X, level1Y, level2, level2X, level2Y);
    }

    /**
     * Transitions the given character to the other point connected to
     * their current location, checking first that this character belongs
     * to a player.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(CharacterManager mgr) {
        // NOTE: we might want a flag on the manager, or even the
        // character, so things can override this behavior
        if (! (mgr instanceof PlayerCharacterManager))
            return false;
        
        handleEntered(mgr);

        return true;
    }

}
