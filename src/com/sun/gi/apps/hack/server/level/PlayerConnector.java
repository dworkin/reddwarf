
/*
 * PlayerConnector.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Tue Mar  7, 2006	 7:48:46 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.PlayerCharacterManager;


/**
 * This is an extension of <code>SimpleConnector</code> that only allows
 * <code>PlayerCharacter</code>s to enter. This lets you create boundries
 * for AI creatures.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class PlayerConnector extends SimpleConnector
{

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
    public PlayerConnector(GLOReference<? extends Level> level1Ref,
                           int level1X, int level1Y,
                           GLOReference<? extends Level> level2Ref,
                           int level2X, int level2Y) {
        super(level1Ref, level1X, level1Y, level2Ref, level2X, level2Y);
    }

    /**
     * Transitions the given character to the other point connected to
     * their current location, checking first that this character belongs
     * to a player.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(GLOReference<? extends CharacterManager>
                                     mgrRef) {
        // NOTE: we might want a flag on the manager, or even the
        // character, so things can override this behavior
        if (! (mgrRef.peek(SimTask.getCurrent()) instanceof
               PlayerCharacterManager))
            return false;
        
        handleEntered(mgrRef);

        return true;
    }

}
