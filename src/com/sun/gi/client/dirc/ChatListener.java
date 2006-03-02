/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 *
 * ChatListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 6:08:45 PM
 * Desc: 
 *
 */

package com.sun.gi.client.dirc;

/**
 * Listener interface for incoming chat messages.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface ChatListener
{

    /**
     * Notify the listener when a message has arrived.
     *
     * @param sender  the name of the sender.  null means it's the server.
     * @param channel the channel the message came in on, if any
     * @param message the messsage itself
     */
    public void messageArrived(String sender, String channel, String message);

    public void info(String message);
}
