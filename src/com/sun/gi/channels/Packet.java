package com.sun.gi.channels;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface Packet {
  public static final long NO_MASK = 0;

  public long getSenderID();

  public void setSenderID(long ID);

  public long getMask();

  public void setMask(long mask);

  public boolean getReliable();

  public void setReliable(boolean reliable);

  public void setInOrder(boolean inOrder);

  public boolean getInOrder();

  public int getLength();

  public void setLength(int length);

  public void setOffset(int length);

  public int getOffset();

  public void setData(byte[] data) throws DataTooLargeException;

  public byte[] getData();

  void copyData(byte[] data);
}