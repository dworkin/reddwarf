
/*
 * PlayerCharacterManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Mar  4, 2006	 3:36:22 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.Level;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;


/**
 * This is an implementation of <code>CharacterManager</code> used to manage
 * <code>PlayerCharacter</code>s. This manager can contain any number of
 * characters, though only one is ever the current one playing.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class PlayerCharacterManager extends BasicCharacterManager
{

    // a self-reference
    private GLOReference<? extends CharacterManager> selfRef;

    // a reference to the owning player
    private GLOReference<Player> playerRef;

    // the characters currently owned by this manager
    private HashMap<String,PlayerCharacter> characterMap;

    // the currently playing character
    private PlayerCharacter currentCharacter;

    /**
     * Creates an instance of <code>PlayerCharacterManager</code>.
     *
     * @param playerRef a reference to the owning <code>Player</code>
     */
    public PlayerCharacterManager(GLOReference<Player> playerRef) {
        super("player:" + playerRef.peek(SimTask.getCurrent()).getName());

        this.playerRef = playerRef;

        characterMap = new HashMap<String,PlayerCharacter>();
        currentCharacter = null;
    }

    /**
     * Returns a reference to the <code>Player</code> that owns this manager.
     *
     * @return the owning <code>Player</code>
     */
    public GLOReference<Player> getPlayerRef() {
        return playerRef;
    }

    /**
     * Sets our self-reference.
     *
     * @param selfRef the self-reference
     */
    public void setRef(GLOReference<? extends CharacterManager> selfRef) {
        this.selfRef = selfRef;
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
     * Sets the current character, if the given name is known.
     *
     * @param characterName the name of the character
     *
     * @return true if the current character was set, false otherwise
     */
    public boolean setCurrentCharacter(String characterName) {
        currentCharacter = characterMap.get(characterName);

        return (currentCharacter != null);
    }

    /**
     * Returns the current character being played through this manager.
     *
     * @return the current character
     */
    public Character getCurrentCharacter() {
        return currentCharacter;
    }

    /**
     * Returns the characters managed by this class.
     *
     * @return the managed characters
     */
    public Collection<PlayerCharacter> getCharacters() {
        return characterMap.values();
    }

    /**
     * Returns the names of the characters managed by this class.
     *
     * @return the names of the managed characters 
     */
    public Set<String> getCharacterNames() {
        return characterMap.keySet();
    }

    /**
     * Adds a character to this manager.
     *
     * @parameter character the character to add
     */
    public void addCharacter(PlayerCharacter character) {
        characterMap.put(character.getName(), character);
    }

    /**
     * Tries to remove the given sharacter from the manager.
     *
     * @param name the character to remove
     *
     * @return true if the removal succeded, false otherwise
     */
    public void removeCharacter(String name) {
        characterMap.remove(name);
    }

    /**
     * Sends the given board to the player.
     *
     * @param board the board to send
     */
    public void sendBoard(Board board) {
        SimTask task = SimTask.getCurrent();
        playerRef.peek(task).sendBoard(task, board);
    }

    /**
     * Sends space updates to the player.
     *
     * @param updates the updates to send
     */
    public void sendUpdate(Collection<BoardSpace> updates) {
        SimTask task = SimTask.getCurrent();
        playerRef.peek(task).sendUpdate(task, updates);
    }

    /**
     * Sends the current character's stats to the player.
     */
    public void sendCharacter() {
        SimTask task = SimTask.getCurrent();
        playerRef.peek(task).sendCharacter(task, currentCharacter);
    }

}
