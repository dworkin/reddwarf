package com.sun.gi.demo;

import java.io.*;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.logic.*;

public class WurmPlayer implements Serializable, SimUserDataListener,
    WurmClientListener {
  private SOReference wurmBoot;
  private String name;
  private UserID uid;
  private long WURMid;
  private float xpos=256;
  private float ypos=256;
  private float zpos=2;
  private float xrot=0;
  private float yrot=0;

  private static final long serialVersionUID = -560245896319031239L; // turn off version checking

  public WurmPlayer(SOReference wurmBoot, String name, UserID id, long WURMid) {
    this.name = name;
    this.wurmBoot = wurmBoot;
    uid = id;
    this.WURMid = WURMid;
  }

  public void setPosition(float x,float y,float z,float xr, float yr){
    xpos = x;
    ypos = y;
    zpos = z;
    xrot = xr;
    yrot = yr;
  }

  /**
   * userDataReceived
   *
   * @param task SimTask
   * @param uid UserID
   * @param data byte[]
   */
  public void userDataReceived(SimTask task, UserID uid, byte[] data) {
    //System.out.println("Received user data");
    WurmProtocol.serverProcess(task, this,uid,data,data.length);
  }

  // WurmClientListener callbacks
  /**
   * setPlayerPos
   *
   * @param from UserID
   * @param x float
   * @param y float
   * @param z float
   * @param xrot float
   * @param yrot float
   */
  public void setPlayerPos(SimTask task, UserID from, float x, float y, float z, float xrot,
                           float yrot) {
    byte dx = (byte) ( (x - this.xpos) * 10);
    byte dy = (byte) ( (y - this.ypos) * 10);
    byte dz = (byte) ( (z - this.zpos) * 10);
    byte dyrot = (byte)((yrot/360)*256);
    setPosition(x, y, z, xrot, yrot);
    WurmBoot bootObject = (WurmBoot) wurmBoot.peek(task);
    UserID[] currentUsers = bootObject.currentUsers();
    if (currentUsers.length >1 ) {
      byte[] buff = WurmProtocol.makeMoveCreaturePacket(WURMid, dx, dy, dz, dyrot);
      int c = 0;
      List outusers = new ArrayList();
      for (int i = 0; i < currentUsers.length; i++) {
        if (!currentUsers[i].equals(uid)) {
         outusers.add(currentUsers[i]);
        }
      }
      UserID[] otherusers = new UserID[outusers.size()];
      outusers.toArray(otherusers);
      task.sendData(otherusers, bootObject.myUserID, buff);
    }
  }

  /**
   * playerMessage
   *
   * @param task SimTask
   * @param type int
   * @param msg String
   * @param receiver String
   */
  public void playerMessage(SimTask task, int type, String msg, String receiver) {
    WurmBoot bootObject = (WurmBoot)wurmBoot.peek(task);
    UserID[] ids = bootObject.currentUsers();
    byte[] buff = WurmProtocol.makeMessageToPlayer(type,name,msg);
    task.sendData(ids,uid,buff);
  }

  /**
   * getX
   *
   * @return float
   */
  public float getX() {
    return xpos;
  }

  /**
   * getY
   *
   * @return float
   */
  public float getY() {
    return ypos;
  }

  /**
   * getZ
   *
   * @return float
   */
  public float getZ() {
    return zpos;
  }

  /**
   * getYRot
   *
   * @return float
   */
  public float getYRot() {
    return yrot;
  }

  /**
   * getWurmID
   *
   * @return long
   */
  public long getWurmID() {
    return WURMid;
  }

  /**
   * getName
   *
   * @return String
   */
  public String getName() {
    return name;
  }

  /**
   * getUserID
   *
   * @return Object
   */
  public UserID getUserID() {
    return uid;
  }

  /**
  * setUserID
  *
  * @param id
  * @return void
  */
 public void setUserID(UserID id) {
   uid = id;
 }

  /**
   * playerListRequest
   */
  public void playerListRequest() {
  }

  /**
   * wizardOfOz
   *
   * @param target String
   */
  public void wizardOfOz(String target) {
  }

  /**
   * setWurmID
   *
   * @param i int
   */
  public void setWurmID(int i) {
    WURMid = i;
  }

}
