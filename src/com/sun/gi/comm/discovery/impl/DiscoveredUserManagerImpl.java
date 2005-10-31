package com.sun.gi.comm.discovery.impl;

import java.util.*;

import com.sun.gi.comm.discovery.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DiscoveredUserManagerImpl implements DiscoveredUserManager{
  /**
    * DiscoveredUserManagerImpl
    *
    * @param class Class
    */
   String clientClass;
   Map<String,String> parameters = new HashMap<String,String>();
  
   public DiscoveredUserManagerImpl(String clientClass) {
     this.clientClass = clientClass;
   }

  /**
   * getClientClass
   *
   * @return String
   */
  public String getClientClass() {
    return clientClass;
  }

  /**
   * getParameter
   *
   * @param tag String
   * @return String
   */
  public String getParameter(String tag) {
    return (String)parameters.get(tag);
  }

  /**
   * addParameter
   *
   * @param tag String
   * @param value String
   */
  public void addParameter(String tag, String value) {
    parameters.put(tag,value);
  }

  

}
