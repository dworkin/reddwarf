package com.sun.gi.comm.users.client;

import com.sun.gi.comm.discovery.DiscoveredUserManager;
import com.sun.gi.comm.discovery.Discoverer;
import com.sun.gi.comm.discovery.DiscoveredGame;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DefaultUserManagerPolicy implements UserManagerPolicy{
  public DefaultUserManagerPolicy() {
  }

  /**
   * choose
   * IMPORTANT: Thsi currently ignores manager type selection. Thats
   * bad and a bug!
   * @param discoverer Discoverer
   * @param umanagerName String
   * @return DiscoveredUserManager
   */
  public DiscoveredUserManager choose(DiscoveredGame game,
                                      String umanagerName) {
    DiscoveredUserManager[] mgrs = game.getUserManagers();
    // totally random
    return mgrs[(int)(Math.random()*mgrs.length)];

  }

}
