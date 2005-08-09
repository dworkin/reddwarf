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

public class UserMgrRec {
  String serverclass;
  Map parameters = new HashMap();
  List loginModules = new ArrayList();
 
  /**
   * UserMgrRec
   *
   * @param uSERMANAGER USERMANAGER
   */
  public UserMgrRec() {
   
  }

  /**
   * getServerClassName
   *
   * @return String
   */
  public String getServerClassName() {
    return serverclass;
  }

  /**
   * getParameterMap
   *
   * @return Object
   */
  public Map getParameterMap() {
    return parameters;
  }

  /**
   * listLoginModules
   *
   * @return Iterator
   */
  public Iterator listLoginModules() {
    return loginModules.iterator();
  }

  /**
   * hasLoginModules
   *
   * @return boolean
   */
  public boolean hasLoginModules() {
    return loginModules.size()>0;
  }



}
