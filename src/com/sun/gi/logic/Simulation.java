package com.sun.gi.logic;

import com.sun.gi.comm.routing.*;
import com.sun.gi.comm.routing.UserID;

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
   * particular game app.  When an event is ocurred, a SmTask is queued for each
   * listenr that will invoke the apropriate event method on the listening
   * GLO.
   *
   * Important: Any GLO that is going to listene for user events must implement
   * the SimUserListener interface.
   *
   * @param ref SOReference A rference to a GLO so add to the user listeners list.
   */


  public void addUserListener(SOReference ref);

  /**
   * This call creates a SimTask object that can then be queued for executon.
   *
   * @param ref SOReference A reference to the GLO to invoke to start the task.
   * @param methodName String The name of the method to invoke on the GLO.
   * @param params Object[] The parameters to pass to that method.
   * @return SimTask The created SimTask.
   */
  public SimTask newTask(SOReference ref, String methodName, Object[] params);

  /**
   * Thsi method returns the string that has been assigend as the name of the
   * game app this simulation object was created for.
   * @return String The name of the game
   */
  public String getAppName();

  /**
   * getName
   * @deprecated
   * @return String
   */
  public String getName();

  /**
   * This method returns the long integer ID that was assigend to this game app
   * when it was installed into the backend.
   * @return long The ID.
   */
  public long getAppID();



  /**
   * This method is called by the SimTask method to actually create the user
   * when needed.
   * @return UserID The user ID of the created user.
   */
  public UserID createUser();

  /**
   * This method is called by the SimTask in order to pass a data send up
   * the chain to the kernel and eventually the router.
   *
   * @param to UserID[]  IDs of users to send the data to.
   * @param from UserID ID of user who sent the data (return address)
   * @param bs byte[] The data to send.
   */
  public void sendData(UserID[] to, UserID from, byte[] bs);

  /**
   * This is called  by the SimTask to actually register a user data listener.
   * For more information see the SimTask class.
   *
   * @param id UserID The user ID associated with this listener.
   * @param ref SOReference The reference to the GLO to actually handle the
   * events.
   */
  public void addUserDataListener(UserID id, SOReference ref);

}
