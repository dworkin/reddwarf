package com.sun.gi.gamespy.test;

import com.sun.gi.gamespy.QuereyReportListener;
import com.sun.gi.gamespy.JNIQueryReport;

public class QuereyTest implements QuereyReportListener{
  long sessionID;
  public QuereyTest() {
    JNIQueryReport.addListener(this);
    JNIQueryReport.registerKey(99,"Agent99");
    //JNIQueryReport.registerKey(1,"Jeff's Test");
    sessionID = JNIQueryReport.init("10.5.34.17",1100,"altitude","DZzvoR");
    System.out.println("SessionID = "+sessionID);
    new Thread( new Runnable() {
      public void run() {
        while(true) {
          JNIQueryReport.think(sessionID);
        }
      }
    }).start();
  }

  public static void main(String[] args){
    new QuereyTest();

  }

  /**
   * addError
   *
   * @param errorCode int
   * @param message String
   */
  public void addError(int errorCode, String message) {
    System.out.println("Add Error ("+errorCode+") "+message);
  }

  /**
   * countCallback
   *
   * @param countType int
   * @return int
   */
  public int countCallback(int countType) {
    System.out.println("Count requested of type "+countType);
    return 0;
  }

  /**
   * keyListCallback
   *
   * @param listType int
   * @return int[]
   */
  public int[] keyListCallback(int listType) {
    System.out.println("Key List requested");
    int[] out = {1,3,4,5,6,99};
    return out;
  }

  /**
   * serverKeyCallback
   *
   * @param keyid int
   * @param team int
   * @return byte[]
   */
  public byte[] serverKeyCallback(int keyid) {
    System.out.println("Server Key Value Requested for keyid="+keyid);
    switch(keyid){
      case 1: return "JeffHost\000".getBytes();
      case 3: return "1.0\000".getBytes();
      case 4: return "1141\000".getBytes();
      case 5: return "A Map\000".getBytes();
      case 6: return "A Game Type\000".getBytes();
      case 99: return "GetSmartServer!\000".getBytes();
    }
    return null;
  }

  /**
   * serverKeyCallback
   *
   * @param keyid int
   * @return byte[]
   */
  public byte[] playerKeyCallback(int keyid, int team) {
    System.out.println("Player Key Value Requested for keyid="+keyid+" team="+team);
    switch(keyid){
      case 99: return ("GetSmartClient("+team+")").getBytes();
    }
    return null;
  }

  /**
   * teamKeyCallback
   *
   * @param keyid int
   * @param team int
   * @return byte[]
   */
  public byte[] teamKeyCallback(int keyid, int team) {
    System.out.println("Player Key Value Requested for keyid="+keyid+" team="+team);
    switch(keyid){
      case 99: return ("GetSmartTeam("+team+")").getBytes();
    }
    return null;
  }
}
