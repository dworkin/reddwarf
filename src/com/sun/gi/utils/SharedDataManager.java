package com.sun.gi.utils;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface SharedDataManager {
  public SharedMutex getSharedMutex(String name);
  public SharedData getSharedData(String name);

}