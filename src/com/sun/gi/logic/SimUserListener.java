package com.sun.gi.logic;

import java.io.Serializable;
import com.sun.gi.comm.routing.UserID;

/**
 *
 * <p>Title: SimUserListener</p>
 * <p>Description: This inerface must be implemented by any GLO that
 * is registered to handle game user events</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface SimUserListener extends Serializable{
  /**
   * This method is the start method of a task queued when a user
   * joins the game.
   * @param task SimTask  the SimTask context.
   * @param uid UserID The ID of the user joining.
   * @param loginData byte[] The login data passed by the user.
   */
  public void userJoined(SimTask task, UserID uid, byte[] loginData);
  /**
   *  This method is the start method of a task queued when a user
   *  leavses the game.
   * @param task SimTask  The SimTask context.
   * @param uid UserID The ID of the user leaving the game.
   */
  public void userLeft(SimTask task,UserID uid);
}
