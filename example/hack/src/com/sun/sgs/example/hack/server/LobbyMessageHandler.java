/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.NameNotBoundException;

import java.io.Serializable;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;


/**
 * This <code>MessageHandler</code> is used by <code>Lobby</code> to define
 * and handle all messages sent from the client.
 */
public class LobbyMessageHandler implements MessageHandler, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * Creates a new <code>LobbyMessageHandler</code>.
     */
    public LobbyMessageHandler() {

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

        //System.out.println("Got move request: " + new String(message));

        // the command identifier is always stored in the first byte
        int command = (int)(data.get());

        // FIXME: we should use an enum to define the messages
        //try {
            switch (command) {
            case 1:
                moveToGame(player, data);
                break;
            }
            /*} catch (Exception e) {
            // FIXME: here what we want to do is either log the error, or
            // send back a generic error response
            e.printStackTrace();
            }*/
    }

    /**
     * Used to handle a message requesting a move to some named game.
     */
    private void moveToGame(Player player, ByteBuffer data) {
        // get the length of the game name, and use that to get the game
        int gameLen = data.getInt();
        byte [] bytes = new byte[gameLen];
        try {
            data.get(bytes);
        } catch (BufferUnderflowException bue) {
            // NOTE: I think that this happens when the client thinks it's
            // still in the game and tries to send a move command, but
            // has actually just been bumped back to the lobby...so, for
            // now, the idea is just to drop this message and assume
            // that a valid lobby message will show up soon
            return;
        }
        String gameName = new String(bytes);

        // now get the character that we're playing as
        bytes = new byte[data.remaining()];
        data.get(bytes);
        String characterName = new String(bytes);
        
        // lookup the game, making sure it exists
        DataManager dataManager = AppContext.getDataManager();
        Game game = null;
        try {
            game = dataManager.getBinding(Game.NAME_PREFIX + gameName,
                                          Game.class);
        } catch (NameNotBoundException e) {
            // FIXME: we should send back some kind of error
            System.out.println("Couldn't find game: " +
                               Game.NAME_PREFIX + gameName);
            return;
        }

        if (! player.getCharacterManager().
            setCurrentCharacter(characterName)) {
            // an invalid character name was provided
            // FIXME: we should handle this better
            System.out.println("Invalid character: " + characterName);
            return;
        }

        // queue a task to move the player into another game
        AppContext.getTaskManager().
            scheduleTask(new MoveGameTask(player, game));
    }

}
