package com.sun.gi.framework.install;

import java.util.*;

import com.sun.gi.framework.install.xml.*;

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
  public InstallRec(int id, GAMEAPP gameapp) {
    this.id = id;
    name = gameapp.getGamename();
    BOOTCLASS bcl = gameapp.getBOOTCLASS();
    if (bcl != null) {
      bootClass = bcl.getClassname();
      bootClassParameters = makeParameterMap(bcl.getPARAMETERList());
    }
    for(Iterator iter = gameapp.getUSERMANAGERList().iterator();iter.hasNext();){
      USERMANAGER umgr = (USERMANAGER)iter.next();
      UserMgrRec umgrRec = new UserMgrRec(umgr);
      userManagers.add(umgrRec);
    }

  }

  /**
   * makeParameterMap
   *
   * @param paramList List
   * @return Map
   */
  public static Map makeParameterMap(List paramList) {
    if (paramList == null) {
      return null;
    }
    Map map = new HashMap();
    for(Iterator i = paramList.iterator();i.hasNext();){
      PARAMETER p = (PARAMETER)i.next();
      map.put(p.getTag(),p.getValue());
    }
    return map;
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
