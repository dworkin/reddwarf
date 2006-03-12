
/*
 * OneWayConnector.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Mar  5, 2006	10:39:24 PM
 * Desc: 
 *
 */
package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;


/**
 * This is a <code>Connector</code> that transitions in only one direction.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class OneWayConnector implements Connector
{

    // the target level
    private GLOReference<? extends Level> levelRef;

    // the target position
    private int xPos;
    private int yPos;

    /**
     * Creates an instance of <code>OneWayConnector</code>.
     *
     * @param levelRef a reference to a level that this connects to
     * @param xPos the x-coord on the level this connects to
     * @param yPos the y-coord on the level this connects to
     */
    public OneWayConnector(GLOReference<? extends Level> levelRef,
                           int xPos, int yPos) {
        this.levelRef = levelRef;
        this.xPos = xPos;
        this.yPos = yPos;
    }

    /**
     * Transitions the given character to the target point.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(GLOReference<? extends CharacterManager>
                                     mgrRef) {
        // this connector is easy...we just dump the player to the position
        levelRef.get(SimTask.getCurrent()).addCharacter(mgrRef, xPos, yPos);

        return true;
    }

}
