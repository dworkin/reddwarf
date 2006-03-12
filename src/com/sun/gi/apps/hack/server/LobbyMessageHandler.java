
/*
 * LobbyMessageHandler.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Feb 27, 2006	 9:03:01 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import java.lang.reflect.Method;

import java.nio.ByteBuffer;


/**
 * This <code>MessageHandler</code> is used by <code>Lobby</code> to define
 * and handle all messages sent from the client.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class LobbyMessageHandler implements MessageHandler
{

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
    public void handleMessage(Player player, ByteBuffer data) {
        // the command identifier is always stored in the first byte
        int command = (int)(data.get());

        // FIXME: we should use an enum to define the messages
        try {
            switch (command) {
            case 1:
                moveToGame(player, data);
                break;
            }
        } catch (NoSuchMethodException nsme) {
            // FIXME: we should log this, and send the client some kind of
            // generic "internal error occured" message
            nsme.printStackTrace();
        } catch (Exception e) {
            // FIXME: here what we want to do is either log the error, or
            // send back a generic error response
            e.printStackTrace();
        }
    }

    /**
     * Used to handle a message requesting a move to some named game.
     */
    private void moveToGame(Player player, ByteBuffer data)
        throws NoSuchMethodException
    {
        SimTask task = SimTask.getCurrent();

        // get the length of the game name, and use that to get the game
        int gameLen = data.getInt();
        byte [] bytes = new byte[gameLen];
        data.get(bytes);
        String gameName = new String(bytes);

        // now get the character that we're playing as
        bytes = new byte[data.remaining()];
        data.get(bytes);
        String characterName = new String(bytes);
        
        // lookup the game, making sure it exists
        GLOReference<Game> gameRef = task.findGLO(Game.NAME_PREFIX + gameName);

        // set our current player
        if (! player.getCharacterManager().get(task).
            setCurrentCharacter(characterName)) {
            // an invalid character name was provided
            // FIXME: we should handle this better
            System.out.println("Invalid character: " + characterName);
            return;
        }
        
        if (gameRef != null) {
            // get a reference to the player, and queue a task to move them
            // into another game
            // FIXME: would it be safe to just invoke this directly?
            GLOReference<Player> playerRef = player.getReference();
            Method method =
                Player.class.getMethod("moveToGame", GLOReference.class);
            task.queueTask(playerRef, method, new Object [] {gameRef});
        } else {
            // FIXME: we should send back some kind of error
            System.out.println("Couldn't find game: " +
                               Game.NAME_PREFIX + gameName);
        }
    }

}
