package com.sun.gi.utils;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.io.InputStream;

/**
 * <p>Title: </p>
 * <p>Description: Thsi interface describes a data type that can be written
 *  and read back to byte streams or buffers</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface Communicable {
  public void write(OutputStream strm);
  public void write(ByteBuffer buffer);
  public void read(InputStream strm);
  public void read(ByteBuffer buff);
  public int ioByteSize();
  public byte[] toByteArray();
}
