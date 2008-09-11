/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

import com.sun.sgs.example.hack.server.BasicCharacterManager;
import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;

import java.util.Collection;


/**
 * This implementation of CharacterManager is used for all AI creatures. It
 * adds the ability to do regular invocations of AI characters and handle
 * their death and re-generation.
 */
public class AICharacterManager extends BasicCharacterManager implements Task {

    private static final long serialVersionUID = 1;

    // the character we're managing
    private AICharacter character;

    /**
     * Creates an instance of <code>AICharacter</code>.
     */
    private AICharacterManager() {
        // since we need a unique identifier for all managers (which players
        // get through their login names), we just use a UUID

	// NOTE: it might also be appropriate to instead use
	//       DataManager.nextBoundName() to locate a unique name
	//       for this entity
        super("ai:" + String.valueOf(Math.random()));
    }

    /**
     * Returns a reference to a new instance of <code>AICharacterManager</code>
     * that is registered correctly. After calling this you still need to
     * call <code>setCharacter</code> to give this manager a character.
     *
     * @return a new manager
     */
    public static AICharacterManager newInstance() {
        AICharacterManager mgr = new AICharacterManager();
        AppContext.getDataManager().setBinding(mgr.toString(), mgr);
        return mgr;
    }

    /**
     * Returns the current character being played through this manager. Since
     * <code>AICharacterManager</code>s only have one character that they
     * manage, the current character is always the same.
     *
     * @return the current character
     */
    public Character getCurrentCharacter() {
	AppContext.getDataManager().markForUpdate(this);
        return character;
    }

    /**
     * Sets the character for this manager.
     *
     * @param character the character to manage
     */
    public void setCharacter(AICharacter character) {
        AppContext.getDataManager().markForUpdate(this);

        this.character = character;
    }

    /**
     * Tells the AI creature that it's their turn to take some action.
     */
    public void run() throws Exception {
	AppContext.getDataManager().markForUpdate(this);
        character.run();
    }

    /**
     * Notify the manager that its character has died. This will typically
     * result in re-generation of the character after some period of time.
     */
    public void notifyCharacterDied() {
        // NOTE: based on some aspect of the character's stats, and
        //       possibly other parameters that aren't here yet, we
        //       decide how and where to place a new character ... for
        //       now, we take the simple approach of just adding a new
        //       character
	AppContext.getDataManager().markForUpdate(this);
        character.regenerate();
        getCurrentLevel().addCharacter(this);
    }

    /**
     * Sends the given board to this AI creature. Many AIs will ignore this
     * message, but some use it to construct a view of the world
     *
     * @param board the board to send
     */
    public void sendBoard(Board board) {
        // not currently used
    }

    /**
     * Sends space updates to this AI creature. Many AIs will ignore this
     * message, but some use it to construct a view of the world
     *
     * @param updates the updates to send
     */
    public void broadcastUpdates(Collection<BoardSpace> updates) {
        // not currently used
    }

}
