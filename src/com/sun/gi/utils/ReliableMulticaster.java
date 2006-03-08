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

public interface ReliableMulticaster {
  public void send(DatagramPacket pkt);
  public void setListener(RMCListener l);
}