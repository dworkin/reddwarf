package com.sun.gi.comm.routing;

import java.nio.*;

/**
 * This interface defines the fundemental functionality of a Router. Routers
 * create and dispose of UserIds and move messages to users by way of their
 * user IDs.
 *
 * <p>Title: Router</p>
 * <p>Description:  A tier 1 message router</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface Router {
  /**
   * This call is made in order to allocate a new unqiue UserID.
   * @return UserID  The new ID
   */
  public UserID createUser();

  /**
   * This call is made in order to reconsitute an ID from a  byte array.
   * @return UserID  The new ID
   */
  public UserID createUser(byte[] bytes)throws InstantiationException;


  /**
   * This call is used to free a UserID that is no longer needed.
   * @param id UserID The ID to dispose.
   */
  public void disposeUser(UserID id);
  
  public RouterChannel openChannel(String name);

  
  /**
   * This call adds a listener to be informed when data arrives or when a
   * user ID is destroyed.
   *
   * @param listener RouterListener The object to call back on.
   */
  public void addRouterListener(RouterListener listener);


  /**
   * /**
   * This call is made in order to reattach an already created user
   * thatw as dropped or moved from a different router
   * @param newid UserID  The user ID
   * @param key long A time limited reconnect key previously granted by a router
   * @return boolean true if key is valid, false if key is not
   */

  public boolean reregisterUser(UserID id, byte[] key);

  /**
   * initializeIDkey
   *
   * @param newID UserID
   */
   public byte[] initializeIDKey(UserID id);

}
