package com.sun.gi.framework.install;

import java.util.*;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class InstallRec {
  int id;
  String name;
  String bootClass=null;
  Map bootClassParameters = null;
  List userManagers = new ArrayList();
  public InstallRec() {
  }

  /**
   * InstallRec
   *
   * @param i int
   * @param iNSTALLATION INSTALLATION
   */
  public InstallRec(int id) {
    this.id = id;
   
  }

  /**
   * makeParameterMap
   *
   * @param paramList List
   * @return Map
   */
  public static Map makeParameterMap(List paramList) {
    return null;
  }

  /**
   * listUserManagers
   *
   * @return Iterator
   */
  public Iterator listUserManagers() {
    return userManagers.iterator();
  }

  /**
   * userManagerCount
   *
   * @return String
   */
  public int userManagerCount() {
    return userManagers.size();
  }

  /**
   * getID
   *
   * @return int
   */
  public int getID() {
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
   * getDescription
   *
   * @return String
   */
  public String getDescription() {
    return "";
  }

}
