package com.sun.gi.gamespy;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface TransportListener {
  /**
   * socketError
   *
   * @param socketHandle long
   */
  public void socketError(long socketHandle);

  /**
   * connected
   *
   * @param connectionHandle long
   * @param result long
   * @param message byte[]
   * @param msgLength int
   */
  public void connected(long connectionHandle, long result, byte[] message,
                        int msgLength);

  /**
   * closed
   *
   * @param connectionHandle long
   * @param reason long
   */
  public void closed(long connectionHandle, long reason);

  /**
   * ping
   *
   * @param connectionHandle long
   * @param latency int
   */
  public void ping(long connectionHandle, int latency);

  /**
   * connectAttempt
   *
   * @param socketHandle long
   * @param connectionHandle long
   * @param ip long
   * @param port short
   * @param latency int
   * @param message byte[]
   * @param msgLength int
   */
  public void connectAttempt(long socketHandle, long connectionHandle, long ip,
                             short port, int latency, byte[] message,
                             int msgLength);
}
