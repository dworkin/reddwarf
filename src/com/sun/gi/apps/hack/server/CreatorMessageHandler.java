
/*
 * CreatorMessageHandler.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Wed Mar  1, 2006	 3:11:13 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import java.nio.ByteBuffer;


/**
 * This <code>MessageHandler</code> is used by <code>Creator</code> to define
 * and handle all messages sent from the client.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class CreatorMessageHandler implements MessageHandler
{

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
    public void handleMessage(Player player, ByteBuffer data) {
        // the command identifier is always stored in the first byte
        int command = (int)(data.get());

        // FIXME: we should use an enum to define the messages
        try {
            switch (command) {
                // FIXME: implement messages
            }
        } catch (Exception e) {
            // FIXME: here what we want to do is either log the error, or
            // send back a generic error response
        }
    }

}
