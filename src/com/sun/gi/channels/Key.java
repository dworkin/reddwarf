package com.sun.gi.channels;

/**
 * <p>Title: Key</p>
 * <p>Description: This interface defines a Key used to acccess functionality
 * of the system that requries sepcific permissions.  There are two kinds of
 * keys.  Every logged in player is assigned a player key which is used for
 * functionality that is restricted to that player.  Every channel at creation
 * time is assigend a channel key that is returned to the channel creator and
 * is used to access administrative functionality of that particular channel.
 *
 * Embedded in the key is the ID for either the player or the channel which may
 * be retrieved with getID().  The ID is the public identifier for either the
 * player or channel.
 *
 * </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface Key extends Comparable{
    /**
     * This method returns the ID this key is associated with.  If this is
     * a player key then the ID is the associated player's ID.  If this key
     * is a channel key then the ID is the assocaited channel's ID.
     * @return The ID.
     */
    long getID();
}