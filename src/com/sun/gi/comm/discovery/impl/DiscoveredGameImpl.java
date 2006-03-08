package com.sun.gi.comm.discovery.impl;

import com.sun.gi.comm.discovery.DiscoveredGame;
import com.sun.gi.comm.discovery.DiscoveredUserManager;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DiscoveredGameImpl implements DiscoveredGame {
  int id;
  String name;
  DiscoveredUserManager[] userManagers;
  public DiscoveredGameImpl(int gameID, String gameName) {
    id = gameID;
    name = gameName;
  }

  /**
   * getId
   *
   * @return int
   */
  public int getId() {
    return id;
  }

  /**
   * getName
   *
   * @return String
   */
  public String getName() {
    return name;
  }

  /**
   * getUserManagers
   *
   * @return DiscoveredUserManager[]
   */
  public DiscoveredUserManager[] getUserManagers() {
    return userManagers;
  }

  public void setUserManagers(DiscoveredUserManager[] mgrs){
    userManagers = mgrs;
  }

}
