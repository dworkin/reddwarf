package com.sun.gi.utils;

import java.net.DatagramPacket;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.io.ObjectInputStream;
import java.io.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class JRMSSharedMutex implements SharedMutex, JRMSSharedObjectBase {
  static final int STATE_UNLOCKED = 0;
  static final int STATE_LOCKING = 1;
  static final int STATE_LOCKED = 2;
  static final int STATE_NAKED = 3;
  private JRMSSharedDataManager mgr;
  private String name;
  private int state = STATE_UNLOCKED;
  private UUID currentOwner = null;
  Set acksReceived = new TreeSet();

  private static final boolean DEBUG = false;
  public JRMSSharedMutex(JRMSSharedDataManager mgr, String name) {
    this.mgr = mgr;
    this.name = name;

  }

  public int getState() {
    return state;
  }

  public synchronized void lock() {
    if (state == STATE_LOCKED) { // already locked, return
      return;
    }
    else if (state == STATE_UNLOCKED) {
      state = STATE_LOCKING;
      acksReceived.clear();
      mgr.sendLockReq(name);
      while ((state==STATE_LOCKING)||(state==STATE_NAKED)) {
          if (testAcks()) {
              break;
          }
        try {
          wait(mgr.getRosterTimeout());
          mgr.sendLockReq(name);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      //System.out.println("Roster timeout, roster: "+mgr.getRoster()+
      //                       "  received: "+acksReceived);
      state = STATE_LOCKED;
    }
  }

  private synchronized boolean testAcks() {
    Set roster = mgr.getRoster();
    if (state == STATE_NAKED) {
      if (!roster.contains(currentOwner)){ //owner died
        doRelease(currentOwner);
      }
    }
    //System.out.println("Roster size = "+roster.size());
    return acksReceived.containsAll(roster);
  }


  private synchronized void addAck(UUID ackingUUID) {
    if (DEBUG) {
      System.out.println("Adding ACK from "+ackingUUID);
    }
    acksReceived.add(ackingUUID);
    if (testAcks()) {
      notifyAll();
    }
  }

  private synchronized void doNak(UUID nakingUID) {
    if (DEBUG) {
      System.out.println("doing Nak from "+nakingUID);
    }
    currentOwner = nakingUID;
    if (state==STATE_LOCKING) {
      state = STATE_NAKED;
    }
  }

  private synchronized void doRelease(UUID nakingUID) {
    if (DEBUG) {
      System.out.println("doing a release from "+nakingUID);
    }
    if ((state == STATE_NAKED)||(state== STATE_LOCKING)){
      state = STATE_LOCKING;
      acksReceived.clear();
      mgr.sendLockReq(name);
    }
  }


  public synchronized void release() {
    state = STATE_UNLOCKED;
    mgr.sendLockRelease(name);
  }

  public void dataRequest(UUID uuid) {
    System.err.println("ERROR:  Mutex recieved a data request!");
  }

  public void dataAssertion(UUID uuid, byte[] data) {
     System.err.println("ERROR:  Mutex recieved a data assertion");
  }

  public void lockAck(UUID uuid) {
       addAck(uuid);
  }

  public void lockNak(UUID uuid) {
        doNak(uuid);
  }

  public synchronized void lockReq(UUID uuid) {
    if (DEBUG) {
      System.out.println("Lock requested by " + uuid + " our state == " + state
                         +" our UUID = "+mgr.getUUID());
    }
    if (state == STATE_LOCKED) {
      mgr.sendLockNak(name);
    }
    else if ((state == STATE_UNLOCKED)||(state == STATE_NAKED)) {
      mgr.sendLockAck(name);
    }
    else if (state == STATE_LOCKING) {
      if (mgr.getUUID().compareTo(uuid) == -1) {
        mgr.sendLockNak(name);
      }
      else {
        mgr.sendLockAck(name);
        doNak(uuid);
      }
    }
  }


  public void lockRelease(UUID uuid) {
       doRelease(uuid);
  }
}