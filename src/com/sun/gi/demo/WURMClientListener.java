package com.sun.gi.demo;

import com.sun.gi.comm.routing.*;
import com.sun.gi.logic.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface WurmClientListener {
  /**
   * setPlayerPos
   *
   * @param from UserID
   * @param x byte
   * @param y byte
   * @param z byte
   * @param xrot byte
   * @param yrot byte
   */
  public void setPlayerPos(SimTask task, UserID from, float x, float y, float z, float xrot,
                           float yrot);

  /**
   * playerMessage
   *
   * @param task SimTask
   * @param type int
   * @param msg String
   * @param receiver String
   */
  public void playerMessage(SimTask task, int type, String msg, String receiver);

  /**
   * playerListRequest
   */
  public void playerListRequest();

  /**
   * wizardOfOz
   *
   * @param target String
   */
  public void wizardOfOz(String target);
}
