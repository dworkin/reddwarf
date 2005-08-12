package com.sun.gi.utils;

import java.io.*;
import java.nio.*;
import java.security.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class StatisticalUUID
    implements SGSUUID {
  transient static SecureRandom random = null;
  private long randomValue;
  private long timeValue;
  
  public StatisticalUUID(long time, long tiebreaker){
	  timeValue = time;
	  randomValue = tiebreaker;
  }

  public StatisticalUUID() {
    if (random == null) {
      try {
        random = SecureRandom.getInstance("SHA1PRNG");
      }
      catch (NoSuchAlgorithmException ex) {
        ex.printStackTrace();
      }
    }
    randomValue = random.nextLong();
    timeValue = System.currentTimeMillis();
  }

  /**
   * StatisticalUUID
   *
   * @param bs byte[]
   */
  public StatisticalUUID(byte[] bs) throws InstantiationException {
    if (bs.length != 16) {
      throw new InstantiationException();
    }
    randomValue = ((( (long) bs[0])&0xFF) << 56) | ( (( (long) bs[1])&0xFF) << 48) |
        ( (( (long) bs[2])&0xFF) << 40) |
        ( (( (long) bs[3])&0xFF) << 32) |
        ( (( (long) bs[4])&0xFF) << 24) | ( (( (long) bs[5])&0xFF) << 16) |
        ( (( (long) bs[6])&0xFF) << 8) | ((long) bs[7]&0xFF);
    timeValue = ((( (long) bs[8])&0xFF) << 56) | ( (( (long) bs[9])&0xFF) << 48) |
        ( (( (long) bs[10])&0xFF) << 40) |
        ( (( (long) bs[11])&0xFF) << 32) |
        ( (( (long) bs[12])&0xFF) << 24) | ( (( (long) bs[13])&0xFF) << 16) |
        ( (( (long) bs[14])&0xFF) << 8) | ((long) bs[15]&0xFF);

  }

  /**
   * StatisticalUUID
   *
   * @param byteBuffer ByteBuffer
   */
  public StatisticalUUID(ByteBuffer byteBuffer) {
    timeValue = byteBuffer.getLong();
    randomValue = byteBuffer.getLong();
  }

  public int compareTo(Object object) {
    StatisticalUUID other = (StatisticalUUID) object;
    if (timeValue < other.timeValue) {
      return -1;
    }
    else if (timeValue > other.timeValue) {
      return 1;
    }
    else {
      if (randomValue < other.randomValue) {
        return -1;
      }
      else if (randomValue > other.randomValue) {
        return 1;
      }
    }
    return 0;
  }

  public String toString() {
    return ("UUID(" + timeValue + ":" + randomValue + ")");
  }

  public int hashCode() {
    return ( (int) ( (timeValue >> 32) & 0xFFFFFFFF)) ^
        ( (int) (timeValue & 0xFFFFFFFF))
        ^ ( (int) ( (randomValue) >> 32) & 0xFFFFFFFF) ^
        ( (int) (randomValue & 0xFFFFFFFF));
  }

  public boolean equals(Object obj) {
    StatisticalUUID other = (StatisticalUUID) obj;
    return ( (timeValue == other.timeValue) &&
            (randomValue == other.randomValue));
  }

  /**
   * read
   *
   * @param buffer ByteBuffer
   */
  public void read(ByteBuffer buffer) {
    timeValue = buffer.getLong();
    randomValue = buffer.getLong();
  }

  /**
   * read
   *
   * @param strm InputStream
   */
  public void read(InputStream strm) {
    DataInputStream dais = new DataInputStream(strm);
    try {
      timeValue = dais.readLong();
      randomValue = dais.readLong();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * write
   *
   * @param buffer ByteBuffer
   */
  public void write(ByteBuffer buffer) {
    buffer.putLong(timeValue);
    buffer.putLong(randomValue);
  }

  /**
   * write
   *
   * @param strm OutputStream
   */
  public void write(OutputStream strm) {
    DataOutputStream daos = new DataOutputStream(strm);
    try {
      daos.writeLong(timeValue);
      daos.writeLong(randomValue);
      daos.flush();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * ioByteSize
   *
   * @return int
   */
  public int ioByteSize() {
    return 16; // when writing our reading in this value we use 8 bytes
  }

  /**
   * toByteArray
   *
   * @return byte[]
   */
  public byte[] toByteArray() {
    byte[] bytes = new byte[16];
    bytes[0] = (byte) ( (randomValue >> 56) & 0xff);
    bytes[1] = (byte) ( (randomValue >> 48) & 0xFF);
    bytes[2] = (byte) ( (randomValue >> 40) & 0xff);
    bytes[3] = (byte) ( (randomValue >> 32) & 0xff);
    bytes[4] = (byte) ( (randomValue >> 24) & 0xff);
    bytes[5] = (byte) ( (randomValue >> 16) & 0xff);
    bytes[6] = (byte) ( (randomValue >> 8) & 0xff);
    bytes[7] = (byte) ( (randomValue) & 0xff);
    bytes[8] = (byte) ( (timeValue >> 56) & 0xff);
    bytes[9] = (byte) ( (timeValue >> 48) & 0xff);
    bytes[10] = (byte) ( (timeValue >> 40) & 0xff);
    bytes[11] = (byte) ( (timeValue >> 32) & 0xff);
    bytes[12] = (byte) ( (timeValue >> 24) & 0xff);
    bytes[13] = (byte) ( (timeValue >> 16) & 0xff);
    bytes[14] = (byte) ( (timeValue >> 8) & 0xff);
    bytes[15] = (byte) ( (timeValue) & 0xff);
    return bytes;
  }

}
