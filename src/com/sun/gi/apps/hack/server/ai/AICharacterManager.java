
/*
 * AICharacterManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Mar  5, 2006	 3:00:32 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.ai;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.utils.StatisticalUUID;

import com.sun.gi.apps.hack.server.BasicCharacterManager;
import com.sun.gi.apps.hack.server.Character;
import com.sun.gi.apps.hack.server.CharacterManager;

import com.sun.gi.apps.hack.server.level.Level;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;

import java.util.Collection;


/**
 * This implementation of CharacterManager is used for all AI creatures. It
 * adds the ability to do regular invocations of AI characters and handle
 * their death and re-generation.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class AICharacterManager extends BasicCharacterManager
{

    // a self-reference
    private GLOReference<AICharacterManager> selfRef = null;

    // the character we're managing
    private AICharacter character;

    /**
     * Creates an instance of <code>AICharacter</code>.
     */
    private AICharacterManager() {
        // since we need a unique identifier for all managers (which players
        // get through their login names), we just use a UUID
        super("ai:" + (new StatisticalUUID()).toString());
    }

    /**
     * Returns a reference to a new instance of <code>AICharacterManager</code>
     * that is registered correctly. After calling this you still need to
     * call <code>setCharacter</code> to give this manager a character.
     *
     * @return a reference to a new manager
     */
    public static GLOReference<AICharacterManager> newInstance() {
        AICharacterManager mgr = new AICharacterManager();
        mgr.selfRef = SimTask.getCurrent().createGLO(mgr, mgr.toString());
        return mgr.selfRef;
    }

    /**
     * Returns a reference to this manager.
     *
     * @return a self-reference
     */
    public GLOReference<? extends CharacterManager> getReference() {
        return selfRef;
    }

    /**
     * Returns the current character being played through this manager. Since
     * <code>AICharacterManager</code>s only have one character that they
     * manage, the current character is always the same.
     *
     * @return the current character
     */
    public Character getCurrentCharacter() {
        return character;
    }

    /**
     * Sets the character for this manager.
     *
     * @param character the character to manage
     */
    public void setCharacter(AICharacter character) {
        this.character = character;
    }

    /**
     * Tells the AI creature that it's their turn to take some action.
     */
    public void run() {
        character.run();
    }

    /**
     * Notify the manager that its character has died. This will typically
     * result in re-generation of the character after some period of time.
     */
    public void notifyCharacterDied() {
        // FIXME: based on some aspect of the character's stats, and possibly
        // other parameters that aren't here yet, we decide how and where to
        // place a new character ... for now, we take the simple approach
        // of just adding a new character
        character.regenerate();
        getCurrentLevel().get(SimTask.getCurrent()).
            addCharacter(getReference());
    }

    /**
     * Sends the given board to this AI creature. Many AIs will ignore this
     * message, but some use it to construct a view of the world
     *
     * @param board the board to send
     */
    public void sendBoard(Board board) {
        // FIXME: where do I connect this?
    }

    /**
     * Sends space updates to this AI creature. Many AIs will ignore this
     * message, but some use it to construct a view of the world
     *
     * @param updates the updates to send
     */
    public void sendUpdate(Collection<BoardSpace> updates) {
        // FIXME: where do I connect this?
    }

}
