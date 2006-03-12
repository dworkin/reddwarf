
/*
 * MessageHandler.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Feb 27, 2006	 8:53:32 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import java.io.Serializable;

import java.nio.ByteBuffer;


/**
 * This interface is used by classes that consume messages. This is
 * typically used for <code>Game</code> implementations to provide new
 * instances of <code>MessageHandler</code>s to anyone in the game. This
 * pattern lets the game define the logic for messages and how to handle
 * them, but offloads contention to the class (typically <code>Player</code>)
 * that is trying to process a message.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface MessageHandler extends Serializable
{

    /**
     * Called to handle a message.
     *
     * @param player the player associated with the message
     * @param data the message
     */
    public void handleMessage(Player player, ByteBuffer data);

}
