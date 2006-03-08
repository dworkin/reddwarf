package com.sun.gi.utils;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface DistributedMutex {
  public void lock() throws InterruptedException;
  public void release();
  public Object getID();

  public void interrupt();

  public boolean wasInterrupted();
}