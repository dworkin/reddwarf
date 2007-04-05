/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.share.CharacterStats;

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
        int command = (int)(data.get());

        // FIXME: we should use an enum to define the messages
        //try {
            switch (command) {
            case 1:
                // get the id and create the stats
                characterId = data.getInt();
                currentStats = getNewStats(characterId);
                player.sendCharacterStats(-1, currentStats);
                break;
            case 2:
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
            case 3:
                // go to the lobby
                moveToLobby(player);
                break;
            }
            /*} catch (Exception e) {
            // FIXME: here what we want to do is either log the error, or
            // send back a generic error response
            }*/
    }

    /**
     *
     */
    private CharacterStats getNewStats(int id) {
        // FIXME: this should change based on the character class, but for
        // now it's purely random
        int hp = NSidedDie.roll20Sided() + NSidedDie.roll20Sided();
        return new CharacterStats("", NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(),
                                  NSidedDie.roll20Sided(), hp, hp);
    }

    /**
     *
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
     *
     */
    private void moveToLobby(Player player) {
        Lobby lobby = AppContext.getDataManager().
            getBinding(Lobby.IDENTIFIER, Lobby.class);
        AppContext.getTaskManager().
            scheduleTask(new MoveGameTask(player, lobby));
    }

}
