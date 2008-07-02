/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

import java.math.BigInteger;
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
    public void playerJoined(BigInteger uid);

    /**
     * Called when a player leaves the chat group.
     *
     * @param uid the identifier for the player
     */
    public void playerLeft(BigInteger uid);

    /**
     * Notify the listener when a message has arrived.
     *
     * @param sender the identifier for the sender
     * @param message the message itself
     */
    public void messageArrived(String message);

    /**
     * Notifies the listener about some set of mappings from identifier
     * to user name. This information is needed to map names to the
     * other messages received by this interface. It is assumed that
     * user identifiers will not collide during the lifetime of this
     * client, so there is no message to remove or replace existing
     * mappings, but should one of the identifiers in the map collide
     * with an already known identifier, then the existing identifier
     * to name mapping should be replaced by the new one.
     *
     * @param uidMap a map from user identifier to user name
     */
    public void addUidMappings(Map<BigInteger,String> uidMap);

}
