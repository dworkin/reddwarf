package com.sun.gi.utils;

import java.io.Serializable;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface SharedData {
  public void lock();
  public Serializable getValue();
  public void setValue(Serializable value);
  public void release();
}