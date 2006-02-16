package com.sun.gi.comm.discovery.impl;


/**
 * Implementation of a discovered game
 */

public class JMEDiscoveredGameImpl {
  int id;
  String name;
  JMEDiscoveredUserManagerImpl[] userManagers;
  public JMEDiscoveredGameImpl(int gameID, String gameName) {
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
  public JMEDiscoveredUserManagerImpl[] getUserManagers() {
    return userManagers;
  }

  public void setUserManagers(JMEDiscoveredUserManagerImpl[] mgr){
    userManagers = mgr;
  }

}
