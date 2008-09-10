/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

import com.sun.sgs.example.hack.server.BasicCharacterManager;
import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureAttribute;

import java.util.Collection;


/**
 * This implementation of CharacterManager is used for all AI creatures. It
 * adds the ability to do regular invocations of AI characters and handle
 * their death and re-generation.
 */
public class AICharacterManager extends BasicCharacterManager implements Task {

    private static final long serialVersionUID = 1;

    // the character we're managing
    private ManagedReference<AICharacter> characterRef;

    /**
     * Creates an instance of <code>AICharacter</code>.
     */
    protected AICharacterManager() {
        // since we need a unique identifier for all managers (which players
        // get through their login names), we just use a UUID

	// NOTE: it might also be appropriate to instead use
	//       DataManager.nextBoundName() to locate a unique name
	//       for this entity
        super("ai:" + String.valueOf(Math.random()));

	characterRef = null;
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
        return (characterRef == null) ? null : characterRef.get();
    }

    /**
     * Sets the character for this manager.
     *
     * @param character the character to manage
     */
    public void setCharacter(AICharacter character) {
	DataManager dm = AppContext.getDataManager();
	dm.markForUpdate(this);

        characterRef = dm.createReference(character);
    }

    /**
     * Tells the AI creature that it's their turn to take some action.
     */
    public void run() throws Exception {
        characterRef.get().run();
    }

    /**
     * Notify the manager that its character has died. This will
     * typically result in removal of the AI creature and the stopping
     * of its movement.
     */
    public void notifyCharacterDied() {
	// upon character death, remove the current character from the
	// level that it was on and then stop its movement
	Level level = getCurrentLevel();
	if (level != null) {
	    level.removeCharacter(this);
	    
	    // stop ourselves from moving by removing this
	    // CharacterManager, which is an instance of Task
	    AppContext.getDataManager().removeObject(this);
	}
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
