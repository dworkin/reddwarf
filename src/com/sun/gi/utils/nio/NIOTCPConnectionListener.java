package com.sun.gi.utils.nio;

import java.nio.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface NIOTCPConnectionListener {
  /**
   * packetReceived
   *
   * @param nIOTCPConnection NIOTCPConnection
   * @param inputBuffer ByteBuffer
   */
  public void packetReceived(NIOTCPConnection conn,
                             ByteBuffer inputBuffer);


  /**
   * disconnected
   *
   * @param nIOTCPConnection NIOTCPConnection
   */
  public void disconnected(NIOTCPConnection nIOTCPConnection);
}
