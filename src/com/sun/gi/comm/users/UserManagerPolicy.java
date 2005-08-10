package com.sun.gi.comm.users.client;

import com.sun.gi.comm.discovery.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface UserManagerPolicy {
  /**
   * choose
   *
   * @param discoverer Discoverer
   * @param umanagerName String
   * @return DiscoveredUserManager
   */
  public DiscoveredUserManager choose(DiscoveredGame game,
                                      String umanagerName);
}
