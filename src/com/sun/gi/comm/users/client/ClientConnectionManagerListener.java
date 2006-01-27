package com.sun.gi.comm.users.client;

/**
 * <p>Title: ClientConnectionManagerListener
 * <p>Description: This interface defines a listener who recieves events from the
 * ClientConnectionManager.
 * @see ClientConnectionManager 
 * 
 * <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p> New look
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */

import java.nio.*;

import javax.security.auth.callback.*;

/**
 * <p>Title: ClientConnectionManagerListener
 * <p>Description: This interface defines a listener for CLientConnectionManager events.
 * @see ClientConnectionManager 
 * <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p> New look
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */

public interface ClientConnectionManagerListener {
  public void validationRequest(Callback[] callbacks);
  public void connected(byte[] myID);
  public void connectionRefused(String message);
  public void failOverInProgress();
  public void reconnected();
  public void disconnected();
  /**
   * <p>This event is fired when a user joins the game.</p>  
   * When a client initially connects to the game it will receive a userJoined callback for
   * every other user rpesent.  Aftre that every time a new user joins, another callback will be issued.
   *
   * @param userID The ID of the joining user.
   */
  public void userJoined(byte[] userID);

  /**
   * <p>This event is fired whenever a user leaves the game.</p>
   * <p>This occurs either when a user purposefully disconnects or when they drop and do
   * not re-connect within the timeout specified for the reconnection key in the Darkstar
   * backend.</P>
   * 
   * <p><b>Note: In certain rare cases (such as the death of a slice), notification may be delayed.</b>
   * (In the slice-death case it is delayed until a watchdog notices the dead slice.)
   *
   * @param userID The ID of the user leaving the system.
   */
  public void userLeft(byte[] userID);

  
  /**
   * This event is fired to notify the listener of sucessful completion of a channel open operation.
   * 
   * @param channel The channel object used to communicate on the opened channel.
   */
  
  public void joinedChannel(ClientChannel channel);
  
	/**
	 * This method is called whenever an attempted join/leave fails due to 
	 * the target channel being locked.
	 * 
	 * @param channelName		the name of the channel.
	 * @param userID			the ID of the user attemping to join/leave
	 */
  public void channelLocked(String channelName, byte[] userID);
  
}
