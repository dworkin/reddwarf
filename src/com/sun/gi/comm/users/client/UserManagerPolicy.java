package com.sun.gi.comm.users.client;

import com.sun.gi.comm.discovery.DiscoveredUserManager;
import com.sun.gi.comm.discovery.DiscoveredGame;

/**
 * <p>Title: UserManagerPolicy</p>
 * <p>Description: This interface defines a pluggable component
 * used to decide which instance
 * of a USerManager to connect to.</p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc.</p>
 *
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public interface UserManagerPolicy {

  /**
   * Makes a choice among the possible user managers for a given
   * DiscoveredGame
   *
   * @param game  the structure returned from Discovery that describes
   *		  a game and all available conenction points to that
   *              game's Darkstar server.
   *
   * @param umanagerName  the FQCN of the UserManager we have chosen for
   *			  our connection.  A user manager encapsulates
   *			  a connection and transport strategy.
   *
   * @return a DiscoveredUserManager selected by this policy.
   */
  public DiscoveredUserManager choose(DiscoveredGame game,
                                      String umanagerName);
}
