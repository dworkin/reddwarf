package com.sun.gi.comm.routing.old.gtg;

import java.net.DatagramPacket;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class PacketTooLargeException extends Exception {
  DatagramPacket packet;
  public PacketTooLargeException(DatagramPacket packet) {
    this.packet = packet;
  }

  /**
   * Returns the detail message string of this throwable.
   *
   * @return the detail message string of this <tt>Throwable</tt> instance
   *   (which may be <tt>null</tt>).
   * @todo Implement this java.lang.Throwable method
   */
  public String getMessage() {
    return "Packet too big (packet size = "+packet.getLength()+")";
  }

}
