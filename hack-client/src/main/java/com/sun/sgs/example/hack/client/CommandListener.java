/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;


/**
 * This interface defines a class that listens for commands from the player.
 * Specifically, it supports key-presses. It may be extended to support
 * other kinds of commands from the player.
 */
public interface CommandListener
{

    /**
     * Called when the user presses a key.
     *
     * @param key the key, as defined in <code>java.awt.event.KeyEvent</code>
     */
    public void action(int key);

}
