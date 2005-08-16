package com.sun.gi.objectstore.impl;

import com.sun.gi.objectstore.OStoreMetaData;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class TTOStoreMetaData implements OStoreMetaData {
  public static final long METADATAID = 0;
  private long nextObjectID;

  public TTOStoreMetaData(){
    nextObjectID = 1;
  }

  public long getNextObjectID() {
    return nextObjectID;

  }
  public void setNextObjectID(long value) {
    nextObjectID = value;
  }

}