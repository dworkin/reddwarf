package com.sun.gi.gamespy;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface QuereyReportListener {

  /**
   * teamKeyCallback
   *
   * @param keyid int
   * @param team int
   * @return byte[]
   */
  public byte[] teamKeyCallback(int keyid, int team);

  /**
   * serverKeyCallback
   *
   * @param keyid int
   * @param team int
   * @return byte[]
   */
  public byte[] serverKeyCallback(int keyid);

  /**
   * countCallback
   *
   * @param countType int
   * @return int
   */
  public int countCallback(int countType);

  /**
   * keyListCallback
   *
   * @param listType int
   * @return int[]
   */
  public int[] keyListCallback(int listType);

  /**
   * addError
   *
   * @param errorCode int
   * @param message String
   */
  public void addError(int errorCode, String message);

  /**
   * playerKeyCallback
   *
   * @param keyid int
   * @param team int
   * @return byte[]
   */
  public byte[] playerKeyCallback(int keyid, int team);
}
