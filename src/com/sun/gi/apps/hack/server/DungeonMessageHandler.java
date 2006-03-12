
/*
 * DungeonMessageHandler.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Feb 27, 2006	 9:24:09 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.Level;

import com.sun.gi.apps.hack.share.KeyMessages;

import java.nio.ByteBuffer;


/**
 * This <code>MessageHandler</code> is used by <code>Dungeon</code> to define
 * and handle all messages sent from the client.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class DungeonMessageHandler implements MessageHandler
{

    // reference to the associated dungeon
    private GLOReference<Dungeon> dungeonRef;

    /**
     * Creates a new <code>DungeonMessageHandler</code>.
     *
     * @param dungeonRef a reference to the dungeon for which this handler
     *                   handles messages
     */
    public DungeonMessageHandler(GLOReference<Dungeon> dungeonRef) {
        this.dungeonRef = dungeonRef;
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
                movePlayer(player, data);
                break;
            case 2:
                takeItem(player, data);
                break;
            case 3:
                equipItem(player, data);
                break;
            case 4:
                useItem(player, data);
                break;
            }
        } catch (Exception e) {
            // FIXME: here what we want to do is either log the error, or
            // send back a generic error response
            System.out.println("Error in handling dungeon message");
            e.printStackTrace();
        }
    }

    /**
     * Used to handle a key-press message.
     */
    private void movePlayer(Player player, ByteBuffer data) {
        short message = data.getShort();
        SimTask task = SimTask.getCurrent();
        CharacterManager mgr = player.getCharacterManager().get(task);
        GLOReference<? extends Level> levelRef = mgr.getCurrentLevel();

        // we have to do this check, 'cause we may have just left the level
        // (eg, because we were killed), but not quite have grabbed the
        // queued task to move us into our new state
        if (levelRef != null)
            levelRef.get(task).move(mgr, (int)message);
    }

    /**
     *
     */
    private void takeItem(Player player, ByteBuffer data) {
        SimTask task = SimTask.getCurrent();
        CharacterManager mgr = player.getCharacterManager().get(task);
        GLOReference<? extends Level> levelRef = mgr.getCurrentLevel();

        // we have to do this check, 'cause we may have just left the level
        // (eg, because we were killed), but not quite have grabbed the
        // queued task to move us into our new state
        if (levelRef != null)
            mgr.getCurrentLevel().get(task).take(mgr);
    }

    /**
     *
     */
    private void equipItem(Player player, ByteBuffer data) {
        // FIXME: implement
    }

    /**
     *
     */
    private void useItem(Player player, ByteBuffer data) {
        // FIXME: implement
    }

}
