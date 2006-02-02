package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;
import java.util.Map;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.discovery.DiscoveredUserManager;

/**
 * This interface defines the client side of a Darkstar UserManager.
 *
 * The UserManagerClient on the client side, and the UserManager
 * on the server side together encapsulate a transport strategy.
 *
 * UserManagers are pluggable on the server side on a per-game and
 * a per-slice basis.  They announce their presence through the
 * Discovery mechanism and rendezvous with the appropriate
 * UserManagerClient on the client side.
 *
 * Note that the method descriptions talk in terms of actiosn
 * taken.  This is from the point of view of the client side code
 * that calls those methods. In actuality all the
 * UserManagerClient/UserManager pair are responsible for is
 * transmitting the request across the connection. The logic that
 * they plug into handles the details of the actual commands.
 *
 * Copyright (c) 2004-2006 Sun Microsystems, Inc.
 *
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public interface UserManagerClient {

  /**
   * This method is called to initiate connection to the
   * UserManager in the Darkstar backend.  Connection does not
   * imply login.  First a UserManagerClient must report a
   * sucessful connection and only then can login be attempted
   * using the login() method below.
   *
   * @param params   a Map of parameters returned from the Discovery
   *                 system that provide connection settings for a
   *		     unique instance of a UserManager
   *
   * @param listener the receiver of communication events
   *
   * @return true if connection started, false if it fails
   *
   * @see UserManagerClientListener
   */
  public boolean connect(Map<String, String> params,
			 UserManagerClientListener listener);

  public boolean connect(DiscoveredUserManager choice,
			 UserManagerClientListener listener);

  /**
   * Initiates the login procedure
   */
  public void login();

  /**
   * A login() request may result in a validationRequest to the
   * UserManagerClientListener.  This method is used to return the
   * filled out Callback structures to the server for validation.
   *
   * @param cbs  The filled out JAAS Callback structures.
   *
   * @see UserManagerClientListener
   */
  public void validationDataResponse(Callback[] cbs);

  /**
   * Log the user out of the system and disconnect
   * them from the Darkstar back-end.
   */
  public void logout();

  /**
   * Send a request to join a channel.  (The
   * ClientConnectionManager calls this "opening" a channel.  The
   * terms in this case are synonymous.)
   *
   * @param channelName The name of the channel to open.
   */
  public void joinChannel(String channelName);

  /**
   * Send a request to leave a channel.
   *
   * @param channelID
   */
  public void leaveChannel(byte[] channelID);

  /**
   * Send a data packet to the game installed in the Darkstar
   * back-end.  It will be handled by whatever Game Logic Object
   * (GLO) has been registered to handle data packets arriving
   * from this particualr user.
   *
   * @param buff the data itself.
   * @param reliable
   */
  public void sendToServer(ByteBuffer buff, boolean reliable);

  /**
   * Send a packet to another user on the given channel
   *
   * @param chanID The comm channel to put the packet on
   * @param to The user the apcket is destined for
   * @param data The packet itself
   * @param reliable Whether delivery gaurantees are required
   */
  public void sendUnicastMsg(byte[] chanID, byte[] to,
	ByteBuffer data, boolean reliable);

  /**
   * Send a packet to a list of users on the given channel
   *
   * @param chanID The comm channel to put the packet on
   * @param to An array of user IDs that the packet is destined for
   * @param data The packet itself
   * @param reliable Whether delivery gaurantees are required
   */

  public void sendMulticastMsg(byte[] chanID, byte[][] to,
	ByteBuffer data, boolean reliable);

  /**
   * Send a packet to all other users on the given channel
   *
   * @param chanID The comm channel to put the packet on
   * @param data The packet itself
   * @param reliable Whether delivery gaurantees are required
   */

  public void sendBroadcastMsg(byte[] chanID, ByteBuffer data,
	boolean reliable);

  /**
   * Reconnect after having been dropped from a connection point.
   * (This could be due to slice failure or rebalancing of
   * load.)  This method allows a user to reconnect and skip the
   * validation phase so long as their time-limited reconnection
   * key is still valid.
   *
   * @param userID The ID of the user trying to reconnect.
   * @param reconnectionKey A time-limited key used to revalidate
   *                        a previously-validated user.
   */
  public void reconnectLogin(byte[] userID, byte[] reconnectionKey);
}
