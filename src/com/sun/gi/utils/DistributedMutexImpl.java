package com.sun.gi.utils;

import java.util.Set;
import java.util.TreeSet;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class DistributedMutexImpl
    implements DistributedMutex, Comparable {
  Thread lockedBy = null;
  String id;
  DistributedMutexMgrImpl mgr;
  Set acksReceived = new TreeSet();
  boolean remoteAck;
  public long tieBreaker;
  boolean interrupted;
  private long startTime = 0;
  private long timeout = 0;
  private SGSUUID blockedOn;
  public DistributedMutexImpl() {
  }

  public DistributedMutexImpl(String idstring,
                              DistributedMutexMgrImpl manager) {
    id = idstring;
    mgr = manager;
  }

  public synchronized void lock() throws InterruptedException {
    // wait for local access
    interrupted = false;
    while (lockedBy != Thread.currentThread()) {
      try {
        if (lockedBy == null) {
          lockedBy = Thread.currentThread();
        }
        else {
          wait();
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      if (interrupted) {
        throw new InterruptedException();
      }
    }
    // now wair for distributed access
    mgr.lockMutex(this);
    if (interrupted) {
      throw new InterruptedException();
    }

  }

  public synchronized void release() {
    mgr.releaseMutex(this);
    lockedBy = null;
    notifyAll();
  }

  public Object getID() {
    return id;
  }

  public void lockAck(SGSUUID senderID, Set currentPeerSet) {
    acksReceived.add(senderID);
    ackTest(currentPeerSet);
  }

  public void ackTest(Set currentPeerSet) {
    if (acksReceived.containsAll(currentPeerSet)) {
      remoteAck = true;
      synchronized (this) {
        notify();
      }
    }

  }

  public void lockNAK(SGSUUID senderID) {
    // currently does nothing
  }

  public void clearAcks() {
    remoteAck = false;
    acksReceived.clear();
  }

  public boolean equals(Object obj) {
    return (compareTo(obj) == 0);
  }

  public int compareTo(Object obj) {
    String otherid;
    if (obj instanceof String) {
      otherid = (String) obj;
    }
    else {
      otherid = ( (DistributedMutexImpl) obj).id;
    }
    return id.compareTo(otherid);
  }

  public void resetTimeout(long timeout) {
    startTime = System.currentTimeMillis();
    this.timeout = timeout;
  }

  public boolean hasExpired(long currentTime) {
    return startTime < (currentTime + timeout);
  }

  public SGSUUID getBlockedOn() {
    return blockedOn;
  }

  public void setBlockedOn(SGSUUID id){
    blockedOn = id;
  }

  public synchronized void interrupt() {
    interrupted = true;
    mgr.interruptMutex(this);
  }

  public boolean wasInterrupted() {
    return interrupted;
  }
}