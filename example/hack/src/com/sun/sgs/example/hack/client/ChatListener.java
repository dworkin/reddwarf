/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.client.SessionId;

import java.util.Map;


/**
 * This interface is used to listen for incoming chat messages and
 * membership change events.
 */
public interface ChatListener
{

    /**
     * Called when a player joins the chat group.
     *
     * @param uid the identifier for the player
     */
    public void playerJoined(SessionId uid);

    /**
     * Called when a player leaves the chat group.
     *
     * @param uid the identifier for the player
     */
    public void playerLeft(SessionId uid);

    /**
     * Notify the listener when a message has arrived.
     *
     * @param sender the identifier for the sender
     * @param message the messsage itself
     */
    public void messageArrived(SessionId sender, String message);

    /**
     * Notifies the listener about some set of mappings from identifier
     * to user name. This informtion is needed to map names to the
     * other messages recieved by this interface. It is assumed that
     * user identifiers will not collide during the lifetime of this
     * client, so there is no message to remove or replace exisiting
     * mappings, but should one of the identifiers in the map collide
     * with an already known identifier, then the existing identifier
     * to name mapping should be replaced by the new one.
     *
     * @param uidMap a map from user identifier to user name
     */
    public void addUidMappings(Map<SessionId,String> uidMap);

}
