package com.sun.gi.comm.routing;

import java.nio.*;

/**
 * This is an interface that any class that wishes to receieve Router events
 * needs to implement.
 * <p>Title: RouterListener</p>
 * <p>Description: A listern to the Router class</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company; Sun Microsystems, TMI </p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface RouterListener {
  /**
   * This method gets called once for every message,ID pair receieved where
   * the to UserID was allocated by this Router.
   * @param to UserID UserID the message is being sent to.
   * @param from UserID userID the message was from (a return address)
   * @param data byte[] A byte array containing the actual message.
   * @param reliable boolean a flag as to whether reliable delivery is requested
   */
  public void dataArrived(UserID to,UserID from, ByteBuffer data,
                          boolean realiable);

  /**
   * This method gets called whenever the Router becomes aware that a
   * a UserID allcoated previously by any Router in the back-end has become
   * deallocated.
   *
   * @param userID UserID The UserID deallocated.
   */
  public void userDropped(UserID userID);

  /**
   *  This method is called whenever a listener becomes aware that a user has been added to
   * the pool of connected users.
   *

   * @param userID UserID
   * @param local boolean
   */

  public void userAdded(UserID userID);

  public void newUserKey(UserID userID, byte[] key);

  /**
   * broadcastDataArrived
   *
   * @param from UserID
   * @param buff ByteBuffer
   * @param reliable boolean
   */
  public void broadcastDataArrived(UserID from, ByteBuffer buff,
                                   boolean reliable);

}


