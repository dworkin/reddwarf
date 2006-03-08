package com.sun.gi.utils;

import com.sun.gi.utils.SGSUUID;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface JRMSChannelRosterManagerListener {
  public void pktArrived(SGSUUID uuid, byte[] buff);
}