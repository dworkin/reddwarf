package com.sun.gi.utils;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface DistributedMutexMgr {
  public DistributedMutex getMutex(String name);
  public void addListener(DistributedMutexMgrListener l);
  public void sendData(byte[] buff);

  public boolean hasPeers();
}