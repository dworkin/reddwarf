package com.sun.gi.comm.routing.old;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface UserListener {
  public void userPacketArrived(UserID to, UserID from, byte[] data);
  public void userAdded(UserID id, byte[] data);
  public void userRemoved(UserID id, byte[] data);
  public void userDataChanged(UserID id, byte[] data);
}
