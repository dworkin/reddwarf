package com.sun.gi.utils;

import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.net.DatagramPacket;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.*;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

class MutexLists {
  List pendingMutexes = new LinkedList();
  List heldMutexes = new LinkedList();
  List blockedMutexes = new LinkedList();
}

class MutexLookup {
  Object id;
  public MutexLookup(Object obj) {
    id = obj;
  }

  public boolean equals(Object obj) {
    return id.equals( ( (DistributedMutex) obj).getID());
  }

  public int compareTo(Object obj) {
    return ( (Comparable) id).compareTo( ( (DistributedMutex) obj).getID());
  }
}

public class DistributedMutexMgrImpl
    implements DistributedMutexMgr, RMCListener {
  transient static SecureRandom random = null;
  static final byte OP_ANNOUNCE = 0;
  static final byte OP_INTRODUCE = 1;
  static final byte OP_REQLOCK = 2;
  static final byte OP_ACKLOCK = 3;
  static final byte OP_NAKLOCK = 4;
  static final byte OP_RELEASE = 5;
  static final byte OP_DATA = 6;

  MutexLists lists = new MutexLists();
  ReliableMulticaster rmc;
  Set peerSet = new TreeSet();
  SGSUUID uuid = new StatisticalUUID();

  private List listeners = new ArrayList();
  private static final boolean DEBUG = false;
  private boolean done = false;
  private static final long TIMEOUT = 3000;
  public DistributedMutexMgrImpl(ReliableMulticaster reliable) {
    rmc = reliable;
    rmc.setListener(this);
    if (random == null) {
      try {
        random = SecureRandom.getInstance("SHA1PRNG");
      }
      catch (NoSuchAlgorithmException ex) {
        ex.printStackTrace();
      }
    }
    rmc.setListener(this);
    startWatchdog();
    sendAnnounce();
  }

  public void startWatchdog(){
    (new Thread() {
      public void run() {
        while (!done) {
          try {
            Thread.sleep(100);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
          long currentTime = System.currentTimeMillis();
          synchronized (lists) {
            for (Iterator i = lists.pendingMutexes.iterator(); i.hasNext(); ) {
              DistributedMutexImpl mutex = (DistributedMutexImpl) i.next();
              if (mutex.hasExpired(currentTime)) {
                // remove remainign peers from current peerset
                Set leftovers = CollectionUtils.minus(peerSet,
                    mutex.acksReceived);
                peerSet.removeAll(leftovers);
                i.remove();
                mutex.ackTest(peerSet);
              }
            }
            for (Iterator i = lists.blockedMutexes.iterator(); i.hasNext(); ) {
              DistributedMutexImpl mutex = (DistributedMutexImpl) i.next();
              if (mutex.hasExpired(currentTime)) {
                peerSet.remove(mutex.getBlockedOn());
                doRelease(mutex);
              }
            }
          }
        }
      }
    }).start();
  }

  public DistributedMutex getMutex(String name) {
    return new DistributedMutexImpl(name, this);
  }

  public void releaseMutex(DistributedMutexImpl mutex) {
    sendRelease(mutex.getID());
  }

  public void lockMutex(DistributedMutex mutex) throws InterruptedException {
    if (peerSet.size() == 0) {
      if (DEBUG) {
        //System.out.println("No peers.");

      }
      return;
    }
    synchronized (lists) {
      lists.pendingMutexes.add(mutex);
    }
    ( (DistributedMutexImpl) mutex).clearAcks();
    long tieBreaker = random.nextLong();
    ( (DistributedMutexImpl) mutex).tieBreaker = tieBreaker;
    ( (DistributedMutexImpl) mutex).resetTimeout(TIMEOUT);
    sendLockReq(mutex.getID(), tieBreaker);
    while ( ( (DistributedMutexImpl) mutex).remoteAck == false) {
      synchronized (mutex) {
        try {
          mutex.wait();
        }
        catch (InterruptedException ex) {
          ex.printStackTrace();
        }
        if (mutex.wasInterrupted()) {
          throw new InterruptedException();
        }
      }
    }
  }

  private void sendAnnounce() {
    if (DEBUG) {
      System.out.println("Send announce.");
    }
    ObjectOutputStream oos;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(baos);
      oos.writeObject(uuid);
      oos.writeByte(OP_ANNOUNCE);
      oos.flush();
      byte[] outbuff = baos.toByteArray();
      DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
      rmc.send(outpkt);
    }
    catch (IOException ex) {
      ex.printStackTrace();
      return;
    }
  }

  private void sendLockReq(Object mutexid, long tieBreaker) {
    if (DEBUG) {
      System.out.println("Send lock req");
    }
    ObjectOutputStream oos;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(baos);
      oos.writeObject(uuid);
      oos.writeByte(OP_REQLOCK);
      oos.writeObject(mutexid);
      oos.writeLong(tieBreaker);
      oos.flush();
      byte[] outbuff = baos.toByteArray();
      DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
      rmc.send(outpkt);
    }
    catch (IOException ex) {
      ex.printStackTrace();
      return;
    }
  }

  private void sendLockNAK(Object mutexid, SGSUUID senderID) {
    if (DEBUG) {
      System.out.println("Send lock naq.");
    }
    ObjectOutputStream oos;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(baos);
      oos.writeObject(uuid);
      oos.writeByte(OP_NAKLOCK);
      oos.writeObject(mutexid);
      oos.writeObject(senderID);
      oos.flush();
      byte[] outbuff = baos.toByteArray();
      DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
      rmc.send(outpkt);
    }
    catch (IOException ex) {
      ex.printStackTrace();
      return;
    }
  }

  private void sendLockACK(Object mutexid, SGSUUID senderID) {
    if (DEBUG) {
      System.out.println("Send lock ack.");
    }
    ObjectOutputStream oos;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(baos);
      oos.writeObject(uuid);
      oos.writeByte(OP_ACKLOCK);
      oos.writeObject(mutexid);
      oos.writeObject(senderID);
      oos.flush();
      byte[] outbuff = baos.toByteArray();
      DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
      rmc.send(outpkt);
    }
    catch (IOException ex) {
      ex.printStackTrace();
      return;
    }
  }

  private void sendIntroduce() {
    if (DEBUG) {
      System.out.println("Send introduce.");
    }
    ObjectOutputStream oos;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(baos);
      oos.writeObject(uuid);
      oos.writeByte(OP_INTRODUCE);
      oos.flush();
      byte[] outbuff = baos.toByteArray();
      DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
      rmc.send(outpkt);
    }
    catch (IOException ex) {
      ex.printStackTrace();
      return;
    }
  }

  private void sendRelease(Object mutexid) {
    if (DEBUG) {
      System.out.println("Send release.");
    }
    ObjectOutputStream oos;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(baos);
      oos.writeObject(uuid);
      oos.writeByte(OP_RELEASE);
      oos.writeObject(mutexid);
      oos.flush();
      byte[] outbuff = baos.toByteArray();
      DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
      rmc.send(outpkt);
    }
    catch (IOException ex) {
      ex.printStackTrace();
      return;
    }

  }

  public void pktArrived(DatagramPacket pkt) {
    ObjectInputStream ois;
    ByteArrayInputStream bais = new ByteArrayInputStream(pkt.getData(),
        pkt.getOffset(), pkt.getLength());
    try {
      ois = new ObjectInputStream(bais);
      SGSUUID senderID = (SGSUUID) ois.readObject();
      byte opcode = ois.readByte();
      //System.out.println("Got packet, op =" + opcode);
      switch (opcode) {
        case OP_ANNOUNCE:
          peerSet.add(senderID);
          sendIntroduce();
          break;
        case OP_INTRODUCE:
          peerSet.add(senderID);
          break;
        case OP_REQLOCK:
          Object mutexid = ois.readObject();
          MutexLookup lookuprec = new MutexLookup(mutexid);
          long tieBreaker = ois.readLong();
          synchronized (lists) {
            if (lists.heldMutexes.contains(lookuprec)) {
              sendLockNAK(mutexid, senderID);
            }
            else if (lists.pendingMutexes.contains(lookuprec)) {
              int idx = lists.pendingMutexes.indexOf(lookuprec);
              if (idx >= 0) {
                DistributedMutexImpl localMutex = (DistributedMutexImpl)
                    lists.pendingMutexes.get(idx);
                if (tieBreaker < localMutex.tieBreaker) {
                  doLockBlocked(senderID,localMutex);
                  sendLockACK(mutexid, senderID);
                }
                else {
                  sendLockNAK(mutexid, senderID);
                }
              }
            } else {
             sendLockACK(mutexid,senderID);
            }
          }
          break;
      case OP_ACKLOCK:
        mutexid = ois.readObject();
        lookuprec = new MutexLookup(mutexid);
        SGSUUID ownerid = (SGSUUID) ois.readObject();
        if (uuid.equals(ownerid)) { // ack is for us
          int idx = lists.pendingMutexes.indexOf(lookuprec);
          if (idx >= 0) {
            DistributedMutexImpl mutex = (DistributedMutexImpl)
                lists.pendingMutexes.get(idx);
            mutex.lockAck(senderID, peerSet);
          }
        }
        break;
      case OP_NAKLOCK:
        mutexid = ois.readObject();
        lookuprec = new MutexLookup(mutexid);
        ownerid = (SGSUUID) ois.readObject();
        if (uuid.equals(ownerid)) { // nak is for us
          int idx = lists.pendingMutexes.indexOf(lookuprec);
          if (idx >= 0) {
            DistributedMutexImpl mutex = (DistributedMutexImpl)
                lists.pendingMutexes.get(idx);
            mutex.lockNAK(senderID);
            doLockBlocked(senderID, mutex);
          }
        }
        break;
      case OP_RELEASE:
        mutexid = ois.readObject();
        lookuprec = new MutexLookup(mutexid);
        int idx = lists.blockedMutexes.indexOf(lookuprec);
        if (idx >= 0) {
          DistributedMutexImpl mutex = (DistributedMutexImpl)
              lists.blockedMutexes.get(idx);
          doRelease(mutex);
        }
        break;
      case OP_DATA:
        int bufflen = ois.readInt();
        byte[] buff = new byte[bufflen];
        ois.read(buff);
        doData(buff);
        break;
    }
  }

  catch (Exception ex) {
    ex.printStackTrace();
  }

}

private void doRelease(DistributedMutexImpl mutex) {
  synchronized (lists) {
    mutex.clearAcks(); // reset for new req
    mutex.tieBreaker = random.nextLong(); // so it doesnt get stuck with a bad value
    lists.blockedMutexes.remove(mutex);
    lists.pendingMutexes.add(mutex);
  }
  sendLockReq(mutex.id, mutex.tieBreaker);
}

private void doLockBlocked(SGSUUID id, DistributedMutexImpl localMutex) {
  localMutex.setBlockedOn(id);
  synchronized (lists) {
    lists.pendingMutexes.remove(localMutex);
    lists.blockedMutexes.add(localMutex);
  }
}

public void addListener(DistributedMutexMgrListener l) {
  listeners.add(l);
}

public void doData(byte[] buff) {
  for (Iterator i = listeners.iterator(); i.hasNext(); ) {
    ( (DistributedMutexMgrListener) i.next()).receiveData(buff);
  }
}

public void sendData(byte[] buff) {
  ObjectOutputStream oos;
  try {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    oos = new ObjectOutputStream(baos);
    oos.writeObject(uuid);
    oos.writeByte(OP_DATA);
    oos.writeInt(buff.length);
    oos.write(buff);
    oos.flush();
    byte[] outbuff = baos.toByteArray();
    DatagramPacket outpkt = new DatagramPacket(outbuff, outbuff.length);
    rmc.send(outpkt);
  }
  catch (IOException ex) {
    ex.printStackTrace();
    return;
  }
}

  public boolean hasPeers() {
    return (peerSet.size()>0);
  }

  public void interruptMutex(DistributedMutexImpl mutex) {
    synchronized(mutex) {
      mutex.notifyAll(); // wake them up to check the interrupt flag
    }
  }
}
