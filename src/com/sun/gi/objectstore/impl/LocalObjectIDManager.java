package com.sun.gi.objectstore.impl;

/**
 * <p>Title: LocalObjectIDManager
 * <p>Description: This is a trivial objectID manager that ONLY works if there
 * is one and only one copy in the entire system (which implies only one
 * ObjectStore instance).
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LocalObjectIDManager implements ObjectIDManager {
  private long id = 0;

  public LocalObjectIDManager() {
  }

  public long getNextID() {
    return id++;
  }

}