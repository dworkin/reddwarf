package com.sun.gi.comm.users.client;

import com.sun.gi.comm.discovery.*;

/**
 * <p>Title: UserManagerPolicy</p>
 * <p>Description: This interface defines a pluggable component used to decide which instance
 * of a USerManager to connect to.</p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc.</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */

public interface UserManagerPolicy {
  /**
   * This is the method called to make a choice between all the possible user managers.
   *
   * @param game The structure returned from Discovery that describes a game and all possible 
   * conenction points to that game.
   * @param umanagerName The FQCN of the UserManager we have chosen for our connection.  A user manager 
   * encapsulates a connection and transport strategy. 
   * @return DiscoveredUserManager
   * 
   */
  public DiscoveredUserManager choose(DiscoveredGame game,
                                      String umanagerName);
}
