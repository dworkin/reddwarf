/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.CharacterManager;

import java.io.Serializable;


/**
 * This is a <code>Connector</code> that transitions in only one direction.
 */
public class OneWayConnector implements Connector, Serializable {

    private static final long serialVersionUID = 1;

    // the target level
    private ManagedReference<Level> levelRef;

    // the target position
    private int xPos;
    private int yPos;

    /**
     * Creates an instance of <code>OneWayConnector</code>.
     *
     * @param level a level that this connects to
     * @param xPos the x-coord on the level this connects to
     * @param yPos the y-coord on the level this connects to
     */
    public OneWayConnector(Level level, int xPos, int yPos) {
        levelRef = AppContext.getDataManager().createReference(level);

        this.xPos = xPos;
        this.yPos = yPos;
    }

    /**
     * Transitions the given character to the target point.
     *
     * @param mgr the character's manager
     */
    public boolean enteredConnection(CharacterManager mgr) {
        // this connector is easy...we just dump the player to the position
        levelRef.get().addCharacter(mgr, xPos, yPos);

        return true;
    }

}
