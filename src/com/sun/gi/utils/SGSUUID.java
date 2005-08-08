/*
 * SGSUUID.java
 *
 * Created on August 5, 2005, 3:58 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.gi.utils;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 *
 * @author jeffpk
 */
public interface SGSUUID extends Serializable, Comparable {
      
  public void read(ByteBuffer buffer);
  
  /**
   * read
   *
   * @param strm InputStream
   */
  public void read(InputStream strm);

  /**
   * write
   *
   * @param buffer ByteBuffer
   */
  public void write(ByteBuffer buffer);

  /**
   * write
   *
   * @param strm OutputStream
   */
  public void write(OutputStream strm);

  /**
   * ioByteSize
   *
   * @return int
   */
  public int ioByteSize() ;

  /**
   * toByteArray
   *
   * @return byte[]
   */
  public byte[] toByteArray();
}
