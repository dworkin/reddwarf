package com.sun.gi.objectstore;

import java.io.Serializable;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface OStoreMetaData extends Serializable{
  public long getNextObjectID();

  public void setNextObjectID(long l);
}