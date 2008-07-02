/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;


/**
 * This interface is used by classes that consume messages. This is
 * typically used for <code>Game</code> implementations to provide new
 * instances of <code>MessageHandler</code>s to anyone in the game. This
 * pattern lets the game define the logic for messages and how to handle
 * them, but offloads contention to the class (typically <code>Player</code>)
 * that is trying to process a message.
 */
public interface MessageHandler {

    /**
     * Called to handle a message.
     *
     * @param player the player associated with the message
     * @param data the message
     */
    public void handleMessage(Player player, byte [] message);

}
