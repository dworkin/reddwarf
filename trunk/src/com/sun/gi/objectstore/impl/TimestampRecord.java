package com.sun.gi.objectstore.impl;

import java.sql.Timestamp;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class TimestampRecord {
  public Timestamp timestamp;
  public long tiebreaker;
  public boolean inUse;
  public Timestamp lockTime;
  public TimestampRecord( Timestamp ts, long tbreaker, byte bInUse,
                         Timestamp lockTime) {
    timestamp  = ts;
    tiebreaker = tbreaker;
    inUse = (bInUse == 1);
    this.lockTime = lockTime;
  }

}