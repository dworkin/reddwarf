package com.sun.gi.framework.interconnect;

import java.nio.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface TransportChannelListener {
  public void dataArrived(ByteBuffer buff);

  /**
   * channelClosed
   */
  public void channelClosed();
}
