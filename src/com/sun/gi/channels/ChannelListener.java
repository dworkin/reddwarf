package com.sun.gi.channels;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface ChannelListener extends Comparable{
    /**
     * This method is invoked when a player is added to a channel.
     * @param channelID The ID of the channel the player was added to.
     * @param playerID The id of the player added.
     */
    public void playerAddedToChannel(long channelID, long playerID);
    /**
     * This method is invoked when a player is remvoed from a channel.
     * @param channelID The ID of the channel the player was added to.
     * @param playerID The id of the player added.
     */
    public void playerRemovedFromChannel(long channelID, long playerID);
}