/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.Commands;
import com.sun.sgs.example.hack.share.Commands.Command;

import java.io.Serializable;

import java.nio.ByteBuffer;


/**
 * This <code>MessageHandler</code> is used by <code>Creator</code> to define
 * and handle all messages sent from the client.
 */
public class CreatorMessageHandler implements MessageHandler, Serializable {

    private static final long serialVersionUID = 1;

    // the current character stats
    private CharacterStats currentStats;
    private int characterId;

    /**
     * Creates a new <code>CreatorMessageHandler</code>.
     */
    public CreatorMessageHandler() {

    }

    /**
     * Called when the given <code>Player</code> has a message to handle.
     *
     * @param player the <code>Player</code> who received the message
     * @param data the message to handle
     */
    public void handleMessage(Player player, byte [] message) {
        ByteBuffer data = ByteBuffer.allocate(message.length);
        data.put(message);
        data.rewind();

        // the command identifier is always stored in the first byte
        int encodedCommand = data.getInt();
	Command command = Commands.decode(encodedCommand);

        // NOTE: it would be more elegant to use an enum to define the
        //       messages
	switch (command) {
	case ROLL_FOR_STATS:
	    // get the id and create the stats
	    characterId = data.getInt();
	    currentStats = getNewStats(characterId);
	    player.sendCharacterStats(-1, currentStats);
	    break;
	case CREATE_CURRENT_CLIENT_CHARACTER:
	    // get the name...
	    byte [] bytes = new byte[data.remaining()];
	    data.get(bytes);
	    String characterName = new String(bytes);
	    
	    // ...create the character...
	    PlayerCharacter pc = setupCharacter(player, characterName);
	    player.getCharacterManager().addCharacter(pc);
	    
	    // ...and go to the lobby
	    moveToLobby(player);
	    break;
	case CANCEL_CURRENT_CHARACTER_CREATION:
	    // go to the lobby
	    moveToLobby(player);
	    break;
	default:
	    player.notifyUnhandledCommand(Creator.IDENTIFIER, command);
	}
    }

    


    /**
     * Creates new stats for the provided character
     *
     * @param id the id of the character
     *
     * @return the new stats for the character
     */
    static CharacterStats getNewStats(int id) {
        // NOTE: this should change based on the character class, but
        //       for now it's purely random
        int hp = NSidedDie.roll20Sided() + NSidedDie.roll20Sided();
        return new CharacterStats("", NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(), hp, hp);
    }

    /**
     * Creates a new {@code PlayerCharacter} with the provided {@code
     * name} and a randomly initialized {@link CharacterStats}.
     *
     * @param player the player instance
     * @param name the name of the player
     *
     * @return an initialized {@code PlayerCharacter}.
     */
    private PlayerCharacter setupCharacter(Player player, String name) {
        CharacterStats stats =
            new CharacterStats(name, currentStats.getStrength(),
                               currentStats.getIntelligence(),
                               currentStats.getDexterity(),
                               currentStats.getWisdom(),
                               currentStats.getConstitution(),
                               currentStats.getCharisma(),
                               currentStats.getHitPoints(),
                               currentStats.getMaxHitPoints());

        return new PlayerCharacter(player, characterId, stats);
    }

    /**
     * Schedules a {@code Task} to move the provided {@code Player} to
     * the {@link Lobby}.
     *
     * @param player the player to be moved
     */
    private void moveToLobby(Player player) {
        Lobby lobby = (Lobby) AppContext.getDataManager().
            getBinding(Lobby.IDENTIFIER);
        AppContext.getTaskManager().
            scheduleTask(new MoveGameTask(player, lobby));
    }

}
