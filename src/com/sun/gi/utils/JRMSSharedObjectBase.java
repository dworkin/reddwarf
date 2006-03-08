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
  public void dataRequest(SGSUUID uuid);
  public void dataAssertion(SGSUUID uuid, byte[] data);
  public void lockAck(SGSUUID uuid);
  public void lockNak(SGSUUID uuid);
  public void lockReq(SGSUUID uuid);
  public void lockRelease(SGSUUID uuid);
}