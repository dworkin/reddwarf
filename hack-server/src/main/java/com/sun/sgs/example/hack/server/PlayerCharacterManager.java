/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.app.util.ScalableHashMap;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;

import java.io.Serializable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;


/**
 * This is an implementation of <code>CharacterManager</code> used to manage
 * <code>PlayerCharacter</code>s. This manager can contain any number of
 * characters, though only one is ever the current one playing.
 */
public class PlayerCharacterManager extends BasicCharacterManager
    implements Serializable {

    private static final long serialVersionUID = 1;

    // a reference to the owning player
    private ManagedReference<Player> playerRef;

    /**
     * A mapping from character names to the characters currently
     * owned by this player's manager
     */
    private ManagedReference<ScalableHashMap<String,PlayerCharacter>> 
	characterMapRef;

    // the currently playing character
    private ManagedReference<PlayerCharacter> currentCharacterRef;

    /**
     * Creates an instance of <code>PlayerCharacterManager</code>.
     *
     * @param playerRef a reference to the owning <code>Player</code>
     */
    public PlayerCharacterManager(Player player) {
        super("player:" + player.getName());

	DataManager dm = AppContext.getDataManager();
        playerRef = dm.createReference(player);

        ScalableHashMap<String,PlayerCharacter> characterMap = 
	    new ScalableHashMap<String,PlayerCharacter>();
	characterMapRef = dm.createReference(characterMap);
        currentCharacterRef = null;
    }

    /**
     * Returns a reference to the <code>Player</code> that owns this manager.
     *
     * @return the owning <code>Player</code>
     */
    public Player getPlayer() {
        return playerRef.get();
    }

    /**
     * Sets the current character, if the given name is known.
     *
     * @param characterName the name of the character
     *
     * @return true if the current character was set, false otherwise
     */
    public boolean setCurrentCharacter(String characterName) {
	DataManager dm = AppContext.getDataManager();
        dm.markForUpdate(this);

	PlayerCharacter pc = characterMapRef.get().get(characterName);
        currentCharacterRef = (pc == null) ? null : dm.createReference(pc);

        return (currentCharacterRef != null);
    }

    /**
     * Returns the current character being played through this manager.
     *
     * @return the current character
     */
    public Character getCurrentCharacter() {
        return (currentCharacterRef == null) ? null : currentCharacterRef.get();
    }

    /**
     * Returns the number of characters managed by this class.
     *
     * @return the number of manager characters
     */
    public int getCharacterCount() {
        return characterMapRef.get().size();
    }

    /**
     * Returns the characters managed by this class.
     *
     * @return the managed characters
     */
    public Collection<PlayerCharacter> getCharacters() {
        return characterMapRef.get().values();
    }

    /**
     * Returns the names of the characters managed by this class.
     *
     * @return the names of the managed characters 
     */
    public Set<String> getCharacterNames() {
        return characterMapRef.get().keySet();
    }

    /**
     * Adds a character to this manager.
     *
     * @param character the character to add
     */
    public void addCharacter(PlayerCharacter character) {
        AppContext.getDataManager().markForUpdate(this);

        characterMapRef.get().put(character.getName(), character);
    }

    /**
     * Tries to remove the given sharacter from the manager.
     *
     * @param name the character to remove
     */
    public void removeCharacter(String name) {
        AppContext.getDataManager().markForUpdate(this);

        characterMapRef.get().remove(name);
    }

    /**
     * Sends the given board to the player.
     *
     * @param board the board to send
     */
    public void sendBoard(Board board) {
        playerRef.get().sendBoard(board);
    }

    /**
     * Sends space updates to the player.
     *
     * @param updates the updates to send
     */
    public void broadcastUpdates(Collection<BoardSpace> updates) {
        playerRef.get().broadcastBoardUpdate(updates);
    }

    /**
     * Sends the current character's stats to the player.
     */
    public void sendCharacter() {
        playerRef.get().sendCharacter((currentCharacterRef == null) 
				      ? null : currentCharacterRef.get());
    }

}
