package com.sun.gi.utils;

import java.io.ObjectInputStream;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface JRMSSharedObjectBase {
  public void dataRequest(UUID uuid);
  public void dataAssertion(UUID uuid, byte[] data);
  public void lockAck(UUID uuid);
  public void lockNak(UUID uuid);
  public void lockReq(UUID uuid);
  public void lockRelease(UUID uuid);
}