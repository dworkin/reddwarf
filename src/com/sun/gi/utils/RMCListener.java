package com.sun.gi.utils;

import java.net.DatagramPacket;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface RMCListener {
  public void pktArrived(DatagramPacket pkt);
}