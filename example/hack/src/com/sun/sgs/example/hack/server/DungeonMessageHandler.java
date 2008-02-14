/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.Level;

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
    private ManagedReference dungeonRef;

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
        int command = (int)(data.get());

        // FIXME: we should use an enum to define the messages
        //try {
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
            /*} catch (Exception e) {
            // FIXME: here what we want to do is either log the error, or
            // send back a generic error response
            System.out.println("Error in handling dungeon message");
            e.printStackTrace();
            }*/
    }

    /**
     * Used to handle a key-press message.
     */
    private void movePlayer(Player player, ByteBuffer data) {
        short message = data.getShort();
        CharacterManager mgr = player.getCharacterManager();
        Level level = mgr.getCurrentLevel();

        // we have to do this check, 'cause we may have just left the level
        // (eg, because we were killed), but not quite have grabbed the
        // queued task to move us into our new state
        if (level != null)
            level.move(mgr, (int)message);
    }

    /**
     *
     */
    private void takeItem(Player player, ByteBuffer data) {
        CharacterManager mgr = player.getCharacterManager();
        Level level = mgr.getCurrentLevel();

        // we have to do this check, 'cause we may have just left the level
        // (eg, because we were killed), but not quite have grabbed the
        // queued task to move us into our new state
        if (level != null)
            level.take(mgr);
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
