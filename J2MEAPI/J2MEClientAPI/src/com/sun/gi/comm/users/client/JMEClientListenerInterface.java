/*
 * JMEClientListenerInterface.java
 *
 * Created on February 5, 2006, 9:31 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.gi.comm.users.client;

import com.sun.gi.utils.jme.ByteBuffer;
import com.sun.gi.utils.jme.Callback;

/**
 * Darkstar clients need to implement this interface to receive events from the
 * Darkstar server
 * @author as93050
 */
public interface JMEClientListenerInterface {
    
    /**
     * This event informs the event listener that further information is
     * needed in order to validate the user.
     * @param cbs An array of Callback structures to be filled out and sent back.
     */
    public void validationDataRequest(Callback[] cbs);
    /**
     * This event informs the event listener that the
     * validation has processed successfully and that the user can
     * start communicating with the back-end and other users.
     *
     * @param userID An ID issued to represent this user.  <b>UserIDs are not gauranteed
     * to remain the same between logons.</b>
     */
    public void loginAccepted(byte[] userID);
    
    /**
     * This event informs the event listener that user validation has failed.
     * @param message A string containing an explaination of the failure.
     */
    public void loginRejected(String message);
    
    /**
     * This event informs the event listener of another user of the game.
     * When logon first succeeds there will be one of these callabcks sent for every currently
     * logged-in user of this game.  As other users join, additional callabcks will be issued for them.
     * @param userID The ID of the other user
     */
    public void userLoggedIn(byte[] userID);
    
    /**
     * This event informs the event listener that a user has logged out of the game.
     * @param userID The user that logged out.
     */
    public void userLoggedOut(byte[] userID);
    
    /**
     * This event informs the event listener of the successful opening of a channel.
     * @param channelID The ID of the newly joined channel
     */
    public void joinedChannel(String name, byte[] channelID);
    
    /**
     * This callback informs the user that they have left or been removed from a channel.
     * @param channelID The ID of the channel left.
     */
    public void leftChannel(byte[] channelID);
    
    /**
     * This method is called whenever a new user joins a channel that we have open.
     * A set of these events are sent when we first join a channel-- one for each pre-existing
     * channel mamber.  After that we get thsi event whenever someone new joins the channel.
     * @param channelID The ID of the channel joined
     * @param userID The ID of the user who joined the channel
     */
    
    public void userJoinedChannel(byte[] channelID, byte[] userID);
    
    
    /**
     * This method is called whenever another user leaves a channel that we have open.
     * @param channelID The ID of the channel left
     * @param userID The ID of the user leaving the channel
     */
    public void userLeftChannel(byte[] channelID, byte[] userID);
    
    /**
     * This event informs the listener that data has arrived from the Darkstar server channels.
     * @param chanID The ID of the channel on which the data has been received
     * @param from The ID of the sender of the data
     * @param data The data itself
     */
    
    public void dataReceived(byte[] chanID, byte[] from, ByteBuffer data);
    
    /**
     * receive the server id
     * @param user
     */
    public void recvServerID(byte[] user);
    
    /**
     * Let the client know that we have discovered the games.
     */
    public void discoveredGames();
    
    /**
     * Let the client know about any exceptions that occurred when sending
     * or receiving data from the server 
     */
    public void exceptionOccurred(Exception ex);
    
    
    
    
    
    
        
}
