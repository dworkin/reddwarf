
/*
 * ChatListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 6:08:45 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.routing.UserID;

import java.util.Map;


/**
 * This interface is used to listen for incoming chat messages.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface ChatListener
{

    /**
     *
     */
    public void playerJoined(UserID uid);

    /**
     *
     */
    public void playerLeft(UserID uid);

    /**
     * Notify the listener when a message has arrived.
     *
     * @param the name of the sender
     * @param the messsage itself
     */
    public void messageArrived(UserID sender, String message);

    /**
     *
     */
    public void addUidMappings(Map<UserID,String> uidMap);

}
