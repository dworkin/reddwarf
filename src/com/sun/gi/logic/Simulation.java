package com.sun.gi.logic;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.objectstore.ObjectStore;

/**
 * <p>Title: Simulation</p>
 * <p>Description: This is a defines the API for a wrapper class for all the
 * game specific resources needed to run a game in the backend slice.  One of these is
 * instanced for each game and it in turn holds a refernce back to the SimKernel</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */


public interface Simulation {
	
  /**
   * This call adds an object as a listener for users joining or leaving this
   * particular game app.  When an event ocurrs, a SmTask is queued for each
   * listenr that will invoke the apropriate event method on the listening
   * GLO.
   *
   * Important: Any GLO that is going to listene for user events must implement
   * the SimUserListener interface.
   *
   * @param ref SOReference A rference to a GLO so add to the user listeners list.
   */


  public void addUserListener(GLOReference ref);
  
  /**
   * This is called  by the SimTask to actually register a user data listener.
   * For more information see the SimTask class.
   *
   * @param id UserID The user ID associated with this listener.
   * @param ref SOReference The reference to the GLO to actually handle the
   * events.
   */
  public void addUserDataListener(UserID cid, GLOReference ref);
  
  /** Listen to join/leave events on a particular channel */
  public void addChannelMembershipListener(ChannelID cid, GLOReference ref);

  /**
   * This calls add a GLO as a listener to the data being sent  on a channel.
   * Its should be used very judiciously as the clients chatting abck and forth can
   * easily over-whelm the server.  The usual way to get data to the server is directly from the
   * sending user using addUserDataListener above.  This is more for "evesdropping" where
   * necessary.
   * @param ref  A GLORerence to the GLO to get the callback.
   * @see addUserDataListener
   */
  public void addChannelListener(ChannelID cid, GLOReference ref);

  /**
   * This call creates a SimTask object that can then be queued for executon.
   * The AccessType for the target object will be ACCESS_GET
   *
   * @param ref SOReference A reference to the GLO to invoke to start the task.
   * @param methodToCall The method to invoke on the GLO.
   * @param params Object[] The parameters to pass to that method.
   * @return SimTask The created SimTask.
   */
  public SimTask newTask(GLOReference ref, Method methodToCall, Object[] params);
  
  /**
   * This call creates a SimTask object that can then be queued for executon.
   * The target object will be fetched from the ObjectStore for execution according to the passed
   * in AccessType.  ACCESS_GET will wait for a lock on ref.  ACCESS_PEEK will
   * fetch the last comitted value of ref as a task-local copy.  ACCESS_ATTEMPT
   * will try to lock the object, if it cannot then the entire task will be discared.
   *
   * @param access The kind of access to use to fetch ref for execution(see above).
   * @param ref SOReference A reference to the GLO to invoke to start the task.
   * @param methodToCall The method to invoke on the GLO.
   * @param params Object[] The parameters to pass to that method.
   * @return SimTask The created SimTask.
   */
  public SimTask newTask(ACCESS_TYPE access, GLOReference ref, Method methodToCall, Object[] params);

  /**
   * Thsi method returns the string that has been assigend as the name of the
   * game app this simulation object was created for.
   * @return String The name of the game
   */
  public String getAppName();

  

  /**
   * This method returns the long integer ID that was assigend to this game app
   * when it was installed into the backend.
   * @return long The ID.
   */
  public long getAppID();



  /**
	 * sendMulticastData
	 * 
	 * @param cid
	 * @param targets
	 * @param buff
	 * @param reliable
	 */
	public void sendMulticastData(ChannelID cid, UserID[] targets,
			ByteBuffer buff, boolean reliable);

	/**
	 * sendUnicastData
	 * 
	 * @param cid
	 * @param target
	 * @param buff
	 * @param reliable
	 */
	public void sendUnicastData(ChannelID cid, UserID target, ByteBuffer buff,
			boolean reliable);
	/**
	 * sendBroadcastData
	 * 
	 * @param cid
	 * @param target
	 * @param buff
	 * @param reliable
	 */

	public void sendBroadcastData(ChannelID cid, UserID target,
			ByteBuffer buff, boolean reliable);
	
	
	public boolean hasTasks();
	
	public SimTask nextTask();

	/**
	 * @param impl
	 */
	public void queueTask(SimTask impl);

	/**
	 * @param string
	 * @return
	 */
	public ChannelID openChannel(String string);

	public long registerTimerEvent(long tid, ACCESS_TYPE access, long objID,
			long delay, boolean repeat); 

	/**
	 * @return
	 */
	public ObjectStore getObjectStore();
	
	// Hooks into the RawSocketManager, added 1/16/2006
	
	/**
	 * Requests that a socket be opened at the given host on the given port.
	 * The returned ID can be used for future communication with the socket that will
	 * be opened.  The socket ID will not be valid, and therefore should not be used 
	 * until the connection is complete.  Connection is complete once the 
	 * SimRawSocketListener.socketOpened() call back is called.
	 * 
	 * @param access			the access type (GET, PEEK, or ATTEMPT)
	 * @param objID				a reference to the GLO initiating the connection.
	 * @param host				a String representation of the remote host.
	 * @param port				the remote port.
	 * @param reliable			if true, the connection will use a reliable protocol.
	 * 
	 * @return an identifier that can be used for future communication with the socket.
	 */
	public long openSocket(long socketID, ACCESS_TYPE access, long objID, String host, 
			int port, boolean reliable);

	/**
	 * Sends data on the socket mapped to the given socketID.  This method 
	 * will not return until the entire buffer has been drained.
	 * 
	 * @param socketID			the socket identifier.
	 * @param data				the data to send.  The buffer should be in a ready
	 * 							state, i.e. flipped if necessary. 
	 * 
	 * @return the number of bytes sent.
	 */
	public long sendRawSocketData(long socketID, ByteBuffer data);
	
	/**
	 * Requests that the socket matching the given socketID be closed.
	 * The socket should not be assumed to be closed, however, until the 
	 * call back SimRawSocketListener.socketClosed() is called.
	 * 
	 * @param socketID		the identifier of the socket.
	 */
	public void closeSocket(long socketID);

	/**
	 * @return
	 */
	public long getNextTimerID();

	/**
	 * @return
	 */
	public long getNextSocketID();		
	
	/**
	 * Joins the specified user to the Channel referenced by the
	 * given ChannelID.
	 * 
	 * @param user				the user
	 * @param id				the ChannelID
	 */
	public void join(UserID user, ChannelID id);
	
	/**
	 * Removes the specified user from the Channel referenced by the
	 * given ChannelID.
	 * 
	 * @param user				the user
	 * @param id				the ChannelID
	 */
	public void leave(UserID user, ChannelID id);	
  

	/**
	 * Locks the given channel based on shouldLock.  Users cannot join/leave locked channels
	 * except by way of the Router.
	 * 
	 * @param cid				the channel ID
	 * @param shouldLock		if true, will lock the channel, otherwise unlock it.
	 */
	public void lock(ChannelID cid, boolean shouldLock);
	
	/**
	 * Closes the local view of the channel mapped to ChannelID.
	 * Any remaining users will be notified as the channel is closing.
	 * 
	 * @param id		the ID of the channel to close.
	 */
	public void closeChannel(ChannelID id);	
}
