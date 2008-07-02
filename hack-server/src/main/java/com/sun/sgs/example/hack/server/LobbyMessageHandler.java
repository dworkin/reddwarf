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

        // NOTE: it would be more elegant to use an enum to define the
        //       messages
	switch (command) {
            case 1:
                moveToGame(player, data);
                break;
	    default:
		// NOTE: in a more robust production system what we
		//       should to do is either log the error, or send
		//       back a generic error response
		System.out.println("unhandled lobby command: " + command);
	}
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
            // NOTE: I think that this happens when the client thinks
            //       it's still in the game and tries to send a move
            //       command, but has actually just been bumped back
            //       to the lobby...so, for now, the idea is just to
            //       drop this message and assume that a valid lobby
            //       message will show up soon
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
            game = (Game) dataManager.getBinding(Game.NAME_PREFIX + gameName);
        } catch (NameNotBoundException e) {
            // NOTE: in a more robust system we should send back some
            //       kind of error
            System.out.println("Couldn't find game: " +
                               Game.NAME_PREFIX + gameName);
            return;
        }

        if (! player.getCharacterManager().
            setCurrentCharacter(characterName)) {
            // an invalid character name was provided

            // REMDINER: we should handle this case better
            System.out.println("Invalid character: " + characterName);
            return;
        }

        // queue a task to move the player into another game
        AppContext.getTaskManager().
            scheduleTask(new MoveGameTask(player, game));
    }

}
