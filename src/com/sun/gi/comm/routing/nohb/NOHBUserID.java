package com.sun.gi.comm.routing.nohb;


import java.io.*;
import java.nio.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.utils.*;

public class NOHBUserID implements UserID{
  UUID id;

  public NOHBUserID() {
    id = new StatisticalUUID();
  }

  /**
  * NOHBUserID
  *
  * @param bs byte[]
  */
 public NOHBUserID(byte[] bs) throws InstantiationException {
   id = new StatisticalUUID(bs);
 }

 /**
    * NOHBUserID
    *
    * @param byteBuffer ByteBuffer
    */
   public NOHBUserID(ByteBuffer byteBuffer) {
     id = new StatisticalUUID(byteBuffer);
   }


  /**
   * compareTo
   *
   * @param o Object
   * @return int
   */
  public int compareTo(Object o) {
    NOHBUserID other = (NOHBUserID) o;
    return id.compareTo(other.id);
  }

  public boolean equals(Object o){
    NOHBUserID other = (NOHBUserID) o;
    return id.equals(other.id);
  }

  public String toString(){
    return("NOHBUserID "+id);
  }

  /**
   * Returns a hash code value for the object.
   *
   * @return a hash code value for this object.
   * @todo Implement this java.lang.Object method
   */
  public int hashCode() {
    return id.hashCode();
  }

  /**
   * read
   *
   * @param buffer ByteBuffer
   */
  public void read(ByteBuffer buffer) {
    id.read(buffer);
  }

  /**
   * read
   *
   * @param strm InputStream
   */
  public void read(InputStream strm) {
    id.read(strm);
  }

  /**
   * write
   *
   * @param buffer ByteBuffer
   */
  public void write(ByteBuffer buffer) {
    id.write(buffer);
  }

  /**
   * write
   *
   * @param strm OutputStream
   */
  public void write(OutputStream strm) {
    id.write(strm);
  }

  /**
   * ioByteSize
   *
   * @return int
   */
  public int ioByteSize() {
    return id.ioByteSize();
  }

  /**
   * toByteArray
   *
   * @return byte[]
   */
  public byte[] toByteArray() {
    return id.toByteArray();
  }



}
