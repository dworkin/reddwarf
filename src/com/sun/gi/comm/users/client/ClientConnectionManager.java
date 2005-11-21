package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;

/**
 * <p>Title: ClientConnectionManager
 * <p>Description: This interface defines the central client API for connecting into the Darkstar system.
 * An instance of ClientConnectionManager represents the context of a single user of the Darkstar server.
 * Multiple instances maybe maintained by the same program.  (One 
 * example of where multiple users might be needed is in a load-testing app.) </p>
 * <p>Copyright: Copyright (c) 2005 Sun Microsystems, Inc.</p> New look
 * <p>Company: Sun Microsystems</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 * @see ClientConnectionManagerImpl  
 */



public interface ClientConnectionManager
   {
  
	/**
	 * THis method sets a listener for ClientConnectionManager events.  Only one listener
	 * may be set at a time.  Setting a second listener removes the first one.
	 * 
	 * @param l  The object to listen for events.
	 */
  public void setListener(ClientConnectionManagerListener l);
  
  /**
   * This method returns the class names of all the UserManagers available to connect
   * to.  
   * 
   * <b> NOTE :It is assumed that the game name and discovery information was set in the constructor.</b>
   * @see com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl 
   * 
   * @return A String array containing the FQCNs of all the allowed UserManagers for this game.
   */

  public String[] getUserManagerClassNames();  
  
  /**
   * This method is called to make a connection to a game in the Darkstar backend.
   * A return value of TRUE only means that a connection can be attempted, not that connection has sucessfully 
   * completed.   To know when you are fully connected, use the ClientConnectionManagerListener.
   * 
   * Because a server may linger in the discovery data for a period after it actually dies,
   * or may otherwise be unavailable even though it is in the discovery list,
   * the API will try to initiate multiple connection attempts before
   * giving up and returning false.  The number of attempts it tries, and the tiem it sleeps
   * between attempts are controlled by the system properties "sgs.clientconnmgr.connattempts" and
   * "sgs.clientconnmgr.connwait".  If these are unset default values of 10 attempst and 100ms are used. 
   * 
   * @param userManagerClassName The FQCN of the UserManager to connect to. 
   * @return TRUE if conenction can be attempted, FALSE if not. (For instance
   * if the named userManagerClassName is not supported by the game.) 
   *  
   * @throws ClientAlreadyConnectedException if the ClientConnection Manager is already connected to a game.
   * @see ClientConnectionManagerListener
   */

  public boolean connect(String userManagerClassName) throws 
          ClientAlreadyConnectedException;  
  
  
  /**
   * This method is called to make a connection to a game in the Darkstar backend.
   * A return value of TRUE only means that a connection can be attempted, not that connection has sucessfully 
   * completed.  To know when you are fully connected, use the ClientConnectionManagerListener.
   * 
   * To know when you are fully connected, use the ClientConnectionManagerListener.
   * Because a sever may linger in the discovery data for a period after it actually dies,
   * or may otherwise be unavailable, the API will try to initiate multiple connection attempts before
   * giving up and returning false.  The number of attempts it tries, and the tiem it sleeps
   * between attempts is controlled by the second and third parameter. 
   * 
   * 
   * 
   * @param userManagerClassName The FQCN of the UserManager to connect to.
   * @param connectAttempts The number of times to try to connect before returning false
   * @param the numerb of ms to sleep between connect attempts.
   * @return TRUE if conenction can be attempted, FALSE if not. (For instance
   * if the named userManagerClassName is not supported by the game.) 
   *  
   * @throws ClientAlreadyConnectedException if the ClientConnection Manager is already connected to a game.
   * @see ClientConnectionManagerListener
   */
  
  public boolean connect(String userManagerClassName,int connectAttempts,
		  long msBetweenAttempts)throws 
          ClientAlreadyConnectedException;  
  
  /**
   * This method initiates disconnection from a game in the Darkstar back end.
   * To know when disconnection has occurred, use the callbacks in ClientConnectionManagerListener.
   * @see ClientConnectionManagerListener
   *
   */

  public void disconnect();
   
  /**
   * As part of the connection process, the ClientConnectionManagerListener may recieve a request for
   * validation information.  This method is used to return that validation information to the Darkstar backend.
   * 
   * @param cbs  The filled out validation information
   * @see Callback
   */
  public void sendValidationResponse(Callback[] cbs);

  /**
   * THis method is called in order to send a data packet to the game logic residing on the Darkstar server.  
   * It will be processed by whatever Game Logic Object (GLO) has been registered on the server-side
   * to handle data from this particular user.
   * 
   * @param buff The data to send
   * @param reliable Whether this data should be transmitted reliably or not.
   */
  public void sendToServer(ByteBuffer buff,boolean reliable);
  
  
 
  /**
   * This method is called to open a channel.  Once opened the channel is returned for
   * access via a callback on the registered ClientConnectionManagerListener.
   * @see ClientConnectionManagerListener
   * 
   * @param channelName The name of the channel to open.
   */
  public void openChannel(String channelName);


}
