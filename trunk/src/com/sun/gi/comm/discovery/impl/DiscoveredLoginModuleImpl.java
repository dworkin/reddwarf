package com.sun.gi.comm.discovery.impl;

import com.sun.gi.comm.discovery.DiscoveredLoginModule;

public class DiscoveredLoginModuleImpl implements DiscoveredLoginModule{
  String className;
  public DiscoveredLoginModuleImpl(String cname) {
    className = cname;
  }

  /**
   * getClassName
   *
   * @return String[]
   */
  public String getClassName() {
    return className;
  }
}
