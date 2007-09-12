/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
        super("ai:" + String.valueOf(Math.random()));
    }

    /**
     * Returns a reference to a new instance of <code>AICharacterManager</code>
     * that is registered correctly. After calling this you still need to
     * call <code>setCharacter</code> to give this manager a character.
     *
     * @return a reference to a new manager
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
        getCurrentLevel().addCharacter(this);
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
