package com.sun.gi.gamespy;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class JNIQueryReport {
  static List listeners = new ArrayList();
  static boolean initialized = false;

  static {
    System.loadLibrary("GamespyJNI");
  }

  public JNIQueryReport() throws InstantiationException {
    throw new InstantiationException(
        "Class JNIQueryReport may not be instantiated.");
  }

  static public void addListener(QuereyReportListener l) {
    listeners.add(l);
  }

  static native public long init(String ip, int baseport, String gamename,
                                 String secret_key);

  static native public void shutdown(long sessionID);

  static native public void think(long sessionID);

  static native public void sendStatechanged(long sessionID);

  static native public void registerKey(int keyid, String key);

  // callbacks
  static byte[] serverKeyCallback(int keyid) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      byte[] val = ( (QuereyReportListener) (i.next())).serverKeyCallback(
          keyid);
      if (val != null) {
        return val;
      }
    }
    return null;
  }

  static byte[] teamKeyCallback(int keyid, int team) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      byte[] val = ( (QuereyReportListener) (i.next())).teamKeyCallback(
          keyid, team);
      if (val != null) {
        return val;
      }
    }
    return null;
  }

  static byte[] playerKeyCallback(int keyid, int team) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      byte[] val = ( (QuereyReportListener) (i.next())).playerKeyCallback(
          keyid, team);
      if (val != null) {
        return val;
      }
    }
    return null;
  }

  static int countCallback(int countType) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      int count = ( (QuereyReportListener) (i.next())).countCallback(
          countType);
      if (count >= 0) {
        return count;
      }
    }
    return 0;
  }

  static int[] keyListCallback(int listType){
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      int[] klist = ( (QuereyReportListener) (i.next())).keyListCallback(
          listType);
      if (klist != null) {
        return klist;
      }
    }
    return null;
  }

  static void addError(int errorCode, String message){
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (QuereyReportListener) (i.next())).addError(errorCode,message);
    }
  }
}
