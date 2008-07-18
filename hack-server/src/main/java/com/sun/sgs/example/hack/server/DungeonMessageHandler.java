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

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.share.Commands;
import com.sun.sgs.example.hack.share.Commands.Command;
import com.sun.sgs.example.hack.share.KeyMessages;

import java.io.Serializable;

import java.nio.ByteBuffer;


/**
 * This <code>MessageHandler</code> is used by <code>Dungeon</code> to define
 * and handle all messages sent from the client.
 */
public class DungeonMessageHandler implements MessageHandler, Serializable {

    private static final long serialVersionUID = 1;

    // reference to the associated dungeon
    private ManagedReference<Dungeon> dungeonRef;

    /**
     * Creates a new <code>DungeonMessageHandler</code>.
     *
     * @param dungeonRef a reference to the dungeon for which this handler
     *                   handles messages
     */
    public DungeonMessageHandler(Dungeon dungeon) {
        dungeonRef = AppContext.getDataManager().createReference(dungeon);
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
            case MOVE_PLAYER:
                movePlayer(player, data);
                break;
            case TAKE_ITEM:
                takeItem(player, data);
                break;
            case EQUIP_ITEM:
                equipItem(player, data);
                break;
            case USE_ITEM:
                useItem(player, data);
                break;
	    default: 
		player.notifyUnhandledCommand(dungeonRef.get().getName(), 
					      command);
	}
    }

    /**
     * Handles the player even where a key-press triggers a movement.
     */
    private void movePlayer(Player player, ByteBuffer data) {
        int encoded = data.getInt();
	KeyMessages.Type message = KeyMessages.decode(encoded);
        CharacterManager mgr = player.getCharacterManager();
        Level level = mgr.getCurrentLevel();

        // we have to do this check, because we may have just left the
        // level (eg, because we were killed), but not quite have
        // grabbed the queued task to move us into our new state
        if (level != null)
            level.move(mgr, message);
    }

    /**
     * Handles a player taking an item.
     */
    private void takeItem(Player player, ByteBuffer data) {
        CharacterManager mgr = player.getCharacterManager();
        Level level = mgr.getCurrentLevel();

        // we have to do this check, because we may have just left the
        // level (eg, because we were killed), but not quite have
        // grabbed the queued task to move us into our new state
        if (level != null)
            level.take(mgr);
    }

    /**
     * Not currently implemented.
     */
    private void equipItem(Player player, ByteBuffer data) {
        // TODO: implement
    }

    /**
     * Not currently implemented.
     */
    private void useItem(Player player, ByteBuffer data) {
        // TODO: implement
    }

}
