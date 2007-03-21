/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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
