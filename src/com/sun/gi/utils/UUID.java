package com.sun.gi.utils;

import java.io.*;
import java.nio.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface UUID extends Serializable, Comparable, Communicable{
  /**
   * read
   *
   * @param buffer ByteBuffer
   */
  public void read(ByteBuffer buffer);

}
