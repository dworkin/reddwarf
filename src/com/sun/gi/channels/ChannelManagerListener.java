package com.sun.gi.channels;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface ChannelManagerListener {
    /**
     * This method is invoked when a user successfully connects to the system.
     *
     * Note that user ID's are unique for this session but are not garuanteed
     * to remain the same over multiple connection attempts.
     *
     * @param name The non-unique name of the user.
     * @param userID The unqiue ID assigned to the user.
     */
    public void userCreated(String name, long userID,byte[] userData);
     /** This method is invoked when a user unsuccessfully attempts connection
     * to the system.
     *
     *  Note that user IDs are unique for this session but are not garuanteed
     * to remain the same over multiple connection attempts.
     *
     * @param name The non-unique name of the rejected user.
     * @param userlID The unqiue ID assined to the rejected user.
     */
    public void userRejected(String name,byte[] userData);
    /**
     * This method is invopked when a user disconnects from the system.
     * @param name The non-unique name of the disconnected user.
     * @param userID The unqiue ID of the disconnected user.
     */
    public void userDestroyed(String name, long userID,byte[] userData);
    /**
     * This method is invoked when a new channel is created.
     *
     * Note that channel IDs are unique for this session but are not garuanteed
     * to remain the same over multiple channel creation attempts.
     *
     * @param name The non-unqiue channel name of the newly create channel.
     * @param channelID The unique channelID of the newly created channel.
     */
    public void channelAdded(String name, long channelID);
    /**
     * This method is invoked when the channel is destroyed.
     * @param channelID The unique channelID of the destroyed channel.
     */
    public void channelRemoved(String name, long channelID);
}