package com.sun.gi.framework.interconnect;

import java.io.*;
import java.nio.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface TransportChannel {
  public void sendData(ByteBuffer data) throws IOException;
  public void addListener(TransportChannelListener l);
  
  /**
   * Removes the given listener if it exists.
   * 
   * @param l		the listener to remove
   */
  public void removeListener(TransportChannelListener l);
  
  public void closeChannel();

  /**
   * getName
   *
   * @return String
   */
  public String getName();

  /**
   * sendData
   *
   * @param byteBuffers ByteBuffer[]
   */
  public void sendData(ByteBuffer[] byteBuffers);
  
  public void close();
}
