package com.sun.gi.comm.users.client;


import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;

/**
 * <p>Title: UserManagerClientListener</p>
 * <p>Description: This interface is sued to create an object that will process events from
 * the UserManagerClient</p>
 * <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc</p>
 * <p>Company: Sun Microsystems, Inc.</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 * @see UserManagerClient
 */

public interface UserManagerClientListener {
	/**
	 * This event is fired when a connection to the Darkstar backend has been
	 * successfully made.
	 *
	 */
  public void connected();
  /**
   * This event is fired when the connection to the Darkstar backend is lost.
   *
   */
  public void disconnected();
  /**
   * This callback informs the event listener of the issuance of a 
   * new time-limited reconnection key.
   * @param key The reconnection key.
   * @param ttl The number of seconds this key is valid for.  Note
   * that this is seconds from time of issue and so is a maximum not
   * an absolute measure due to delays in delivery.
   */
  public void newConnectionKeyIssued(byte[] key, long ttl);
  /**
   * This event informs the event listener that further information is
   * needed in order to validate the user.
   * @param cbs An array of JAAS Callback structures to be filled out and sent back.
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
   * @param message A string containign an explaination of the failure.
   */
  public void loginRejected(String message);
  
  /**
   * This event informs the event listener of another user of the game.  
   * When logon first succeeds there will be one of these callabcks sent for every currently
   * logged-in user of this game.  As other users join, additional callabcks will be issued for them.
   * @param userID The ID of the other user
   */
  public void userAdded(byte[] userID);
  
  /**
   * This event informs the event listener that a user has logged out of the game.
   * @param userID The user that logged out.
   */
  public void userDropped(byte[] userID); 
  
  /**
   * This event informs the event listener of the successful openign of a channel.
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
   * This method is called whenevr another user leaves a channel that we have open.
   * @param channelID The ID of the channel left
   * @param userID The ID of the user leaving the channel
   */
  public void userLeftChannel(byte[] channelID, byte[] userID);
  
  /**
   * This event informs the listener that data has arrived from the Darkstar server channels.
   * @param chanID The ID of the channel on which the data has been received
   * @param from The ID of the sender of the data
   * @param data The data itself
   * @param reliable Whether the data was sent with a delivery gaurantee or not.
   */
  
  public void recvdData(byte[] chanID, byte[] from, ByteBuffer data,boolean reliable);
/**
 * @param user
 */
	public void recvServerID(byte[] user);
  
  
}
