package com.sun.gi.comm.routing.old.gtg;

import java.io.*;
import java.net.*;
import java.util.*;

import com.sun.gi.utils.*;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.transport.TransportProfile;
import java.net.DatagramPacket;
import java.util.Set;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

class ManagerRecord
    implements Comparable {
  UUID mid;
  long lastHBtime;
  List users = new LinkedList();

  public ManagerRecord(UUID id) {
    mid = id;
  }

  public void addUser(JRMSUserID user) {
    users.add(user);
  }

  public void removeUser(JRMSUserID user) {
    users.remove(user);
  }

  public int compareTo(Object obj) {
    ManagerRecord other = (ManagerRecord) obj;
    return mid.compareTo(other.mid);
  }
}

class ChanRecord
    implements Serializable { // so we can send them
  String chanName;
  JRMSChannelID id;

  public ChanRecord(String name, JRMSChannelID cid) {
    chanName = name;
    id = cid;
  }
}

class AppRecord {
  String name;
  Map channels = new HashMap();

  public AppRecord(String appname) {
    name = appname;
  }

  public boolean contains(JRMSChannelID id) {
    return channels.keySet().contains(id);
  }

  public void addChannel(String name, JRMSChannelID cid) {
    if (!contains(cid)) {
      channels.put(cid, new ChanRecord(name, cid));
    }
  }

  public ChanRecord getChannel(JRMSChannelID id) {
    return (ChanRecord)channels.get(id);
  }

  public void removeChannel(JRMSChannelID id) {
    channels.remove(id);
  }
}

public class JRMSCommCtrlManager
    implements Runnable, LRMPSocketListener {
  private UUID mgrID;
  private PrimaryChannelManager pcm;
  private LRMPTransportProfile tp;
  private static InetAddress address = null;
  private static int dataPort = 6824;
  private static String addr = "224.100.100.224";
  static final long INVALID_JRMS_CHAN_ID = Long.MIN_VALUE;

  static final long hbrate = 2000; // in ms
  static final byte OP_CTRL_HEARTBEAT = 1;
  static final byte OP_CTRL_NEW_USER = 2;
  static final byte OP_CTRL_DEL_USER = 3;
  static final byte OP_CTRL_JOIN_CHAN = 4;
  static final byte OP_CTRL_LEAVE_CHAN = 5;
  static final byte OP_CTRL_NEW_CHAN = 6;
  static final byte OP_CTRL_DEL_CHAN = 7;
  static final byte OP_DATA_SERVER_PKT = 8;
  static final byte OP_DATA_USER_PKT = 9;
  static final byte OP_DATA_CHANNEL_PKT = 10;
  static final byte OP_CTRL_MGR_INTRO = 11;
  static final byte OP_CTRL_APP_INTRO = 12;

  LRMPSocketManager cmgr;
  DatagramPacket hbpacket;

  Map managerRecords = new HashMap();
  Map appRecords = new HashMap();
  List listeners = new ArrayList();
  List myusers = new LinkedList();
  private int portCounter;
  Object startSemaphore = new Object();
  private boolean initialized = false;

  public JRMSCommCtrlManager() {
    // property based overrides for constants
    String prop = System.getProperty("com.sun.gi.comm.routing.gtg.mcastaddress");
    if (prop != null) {
      addr = prop;
    }
    prop = System.getProperty("com.sun.gi.comm.routing.gtg.mcastport");
    if (prop != null) {
      dataPort = Integer.parseInt(prop);
    }
    try {
      // create control Channel
      mgrID = new StatisticalUUID();
      address = InetAddress.getByName(addr);
      tp = new LRMPTransportProfile(address, dataPort);
      tp.setMaxDataRate(100000);
      tp.setOrdered(true);
      cmgr = new LRMPSocketManager(tp);
      // make heartbeat packet
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_CTRL_HEARTBEAT);
      oos.writeObject(mgrID);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      hbpacket = new DatagramPacket(buff, buff.length);
      cmgr.addListener(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void startHeartbeat() {
    new Thread(this).start();
    try {
      Thread.sleep(hbrate*3);
    } catch (Exception e ){
      e.printStackTrace();
    }
  }

  private void initialized(){
    initialized = true;
    startSemaphore.notifyAll();
  }

  public void addListener(JRMSCtrlListener l) {
    listeners.add(l);
    //send all known manager ifno
    synchronized(managerRecords){
      for(Iterator i = managerRecords.entrySet().iterator();i.hasNext();){
        ManagerRecord rec = (ManagerRecord)i.next();
        l.peerAdded(rec.mid);
        for(Iterator i2 = rec.users.iterator();i2.hasNext();){
          l.userAdded((JRMSUserID)i2);
        }
      }
    }
    // send all known app info
    synchronized(appRecords){
      for(Iterator i = appRecords.values().iterator();i.hasNext();){
        AppRecord rec = (AppRecord)i.next();
        for(Iterator i2 = rec.channels.values().iterator();i2.hasNext();){
            ChanRecord crec = (ChanRecord)i2.next();
            l.channelAdded(rec.name,crec.chanName,crec.id);
        }
      }
    }
  }

  /**
   * run
   */
  public void run() {
    while (true) {
      try {
        cmgr.send(hbpacket);
        updateManagerRecords();
        Thread.sleep(hbrate);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private void addUserToManagerRecord(JRMSUserID uid) {
    ManagerRecord rec;
    synchronized(managerRecords){
      rec = (ManagerRecord) managerRecords.get(uid.managerID);
    }
    if (rec != null) {
      rec.addUser(uid);
    }
  }

  private void removeUserFromManagerRecord(JRMSUserID uid) {
    ManagerRecord rec;
    synchronized(managerRecords){
      rec = (ManagerRecord) managerRecords.get(uid.managerID);
    }
    if (rec != null) {
      rec.removeUser(uid);
    }
  }

  private void updateManagerRecords() {
    long time = System.currentTimeMillis();
    List removedPeers = new ArrayList();
    synchronized (managerRecords) {
      for (Iterator i = managerRecords.values().iterator(); i.hasNext(); ) {
        ManagerRecord rec = (ManagerRecord) i.next();
        if ( (time - rec.lastHBtime) > (3 * hbrate)) { //hb failed
          removedPeers.add(rec);
        }
      }
      for (Iterator i = removedPeers.iterator(); i.hasNext(); ) {
        ManagerRecord rec = (ManagerRecord) i.next();
        managerRecords.remove(rec.mid);
      }
    }
    for (Iterator i = removedPeers.iterator(); i.hasNext(); ) {
      ManagerRecord rec = (ManagerRecord) i.next();
      firePeerRemoved(rec.mid);
      // remove all users logged on to this manager
      for (Iterator pi = rec.users.iterator(); pi.hasNext(); ) {
        JRMSUserID user = (JRMSUserID) pi.next();
        doDeleteUser(user);
      }
    }
  }

  /**
   * firePeerRemoved
   *
   * @param uUID UUID
   */
  private void firePeerRemoved(UUID uUID) {
    synchronized (listeners) {
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).peerRemoved(uUID);
      }
    }
  }

  // client functions
  public JRMSUserID createUser(String appname) {
    JRMSUserID uid = new JRMSUserID(mgrID, appname);
    myusers.add(uid);
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_CTRL_NEW_USER);
      oos.writeObject(uid);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return uid;
  }

  public void removeUser(JRMSUserID uid) {
    myusers.remove(uid);
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_CTRL_DEL_USER);
      oos.writeObject(uid);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void joinChannel(JRMSUserID uid, JRMSChannelID chanid) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_CTRL_JOIN_CHAN);
      oos.writeObject(uid);
      oos.writeObject(chanid);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void leaveChannel(JRMSUserID uid, JRMSChannelID chanid) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_CTRL_LEAVE_CHAN);
      oos.writeObject(uid);
      oos.writeObject(chanid);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void createChannel(String appname, String channame,
                            JRMSChannelID chanid) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_CTRL_NEW_CHAN);
      oos.writeObject(chanid);
      oos.writeObject(appname);
      oos.writeObject(channame);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void removeChannel(JRMSChannelID chanid) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_CTRL_DEL_CHAN);
      oos.writeObject(chanid);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void sendToServer(JRMSUserID from, byte[] data) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_DATA_SERVER_PKT);
      oos.writeObject(from);
      oos.writeObject(data);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void sendToUser(JRMSUserID from, JRMSUserID to, byte[] data) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_DATA_USER_PKT);
      oos.writeObject(from);
      oos.writeObject(to);
      oos.writeObject(data);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void sendToChannel(JRMSChannelID cid, JRMSUserID from, byte[] data) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_DATA_CHANNEL_PKT);
      oos.writeObject(from);
      oos.writeObject(cid);
      oos.writeObject(data);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      baos.close();
      DatagramPacket upacket = new DatagramPacket(buff, buff.length);
      cmgr.send(upacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }




  // callbacks

  /**
   * packetArrived
   *
   * @param jRMSChannelManager JRMSChannelManager
   * @param inpkt DatagramPacket
   */
  public void packetArrived(LRMPSocketManager mgr, DatagramPacket inpkt) {
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(inpkt.getData());
      ObjectInputStream ois = new ObjectInputStream(bais);
      byte op = ois.readByte();
      switch (op) {
        case OP_CTRL_HEARTBEAT:
          UUID hbguid = (UUID) ois.readObject();
          doHeartbeatRecieved(hbguid);
          break;
        case OP_CTRL_NEW_USER:
          JRMSUserID uid = (JRMSUserID) ois.readObject();
          doNewUser(uid);
          break;
        case OP_CTRL_DEL_USER:
          uid = (JRMSUserID) ois.readObject();
          doDeleteUser(uid);
          break;
        case OP_CTRL_JOIN_CHAN:
          uid = (JRMSUserID) ois.readObject();
          JRMSChannelID cid = (JRMSChannelID) ois.readObject();
          doJoinChan(uid, cid);
          break;
        case OP_CTRL_LEAVE_CHAN:
          uid = (JRMSUserID) ois.readObject();
          cid = (JRMSChannelID) ois.readObject();
          doLeaveChan(uid, cid);
          break;
        case OP_CTRL_NEW_CHAN:
          cid = (JRMSChannelID) ois.readObject();
          String appname = (String) ois.readObject();
          String channame = (String) ois.readObject();
          doNewChan(appname, channame, cid);
          break;
        case OP_CTRL_DEL_CHAN:
          cid = (JRMSChannelID) ois.readObject();
          doDelChan(cid);
          break;
        case OP_DATA_SERVER_PKT:
          JRMSUserID user = (JRMSUserID) ois.readObject();
          byte[] buff = (byte[]) ois.readObject();
          doServerPkt(user, buff);
          break;
        case OP_DATA_USER_PKT:
          JRMSUserID from = (JRMSUserID) ois.readObject();
          JRMSUserID to = (JRMSUserID) ois.readObject();
          buff = (byte[]) ois.readObject();
          doUserPkt(from, to, buff);
          break;
        case OP_DATA_CHANNEL_PKT:
          from = (JRMSUserID) ois.readObject();
          cid = (JRMSChannelID) ois.readObject();
          buff = (byte[]) ois.readObject();
          doChannelPkt(from, cid, buff);
          break;

      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * doMgrIntro
   */
  private void doMgrIntro(UUID introMgrID, JRMSUserID[] ids) {
    if (introMgrID == mgrID) {
      return; // its just us
    }
    ManagerRecord rec;
    boolean newrec = false;
    synchronized (managerRecords) {
      rec = (ManagerRecord) managerRecords.get(introMgrID);
      if (rec == null) { // this is new to us
        rec = new ManagerRecord(introMgrID);
        newrec = true;
        rec.lastHBtime = System.currentTimeMillis();
        managerRecords.put(introMgrID, rec);
      }
    }
    if (newrec) {
      synchronized (listeners) {
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
          JRMSCtrlListener l = (JRMSCtrlListener) i.next();
          l.peerAdded(introMgrID);
        }
      }
      synchronized (rec) {
        for (int i = 0; i < ids.length; i++) {
          if (!rec.users.contains(ids[i])) {
            doNewUser(ids[i]);
          }
        }
      }
    }
  }

  public void doAppIntro(String name, ChanRecord[] channels){
    AppRecord app;
    synchronized (appRecords){
      app = (AppRecord) appRecords.get(name);
      if (app == null) { // new app
        app = new AppRecord(name);
        appRecords.put(name, app);
      }
    }
    for(int i=0;i<channels.length;i++){
      if (!app.contains(channels[i].id)){ // new channel
        doNewChan(name,channels[i].chanName,channels[i].id);
      }
    }
  }

  /**
   * doDelChan
   *
   * @param cid JRMSChannelID
   */
  private void doDelChan(JRMSChannelID cid) {
    for(Iterator i = appRecords.values().iterator();i.hasNext();){
      ( (AppRecord) i.next()).removeChannel(cid);
    }
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (JRMSCtrlListener) i.next()).channelRemoved(cid);
    }

  }

  /**
   * doNewChan
   *
   * @param cid UUID
   */
  private void doNewChan(String appname, String channame, JRMSChannelID cid) {
    AppRecord app;
    synchronized(appRecords){
      app = (AppRecord) appRecords.get(appname);
      if (app == null) {
        app = new AppRecord(appname);
        appRecords.put(appname,app);
      }
    }
    ChanRecord chanRec = (ChanRecord)app.getChannel(cid);
    if (chanRec == null) { // new channel
      app.addChannel(channame, cid);
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).channelAdded(appname, channame, cid);
      }
    }
  }

    /**
     * doLeaveChan
     *
     * @param uid JRMSUserID
     * @param cid JRMSChannelID
     */
    private void doLeaveChan(JRMSUserID uid, JRMSChannelID cid) {
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).userRemovedFromChannel(uid, cid);
      }

    }

    /**
     * doJoinChan
     *
     * @param uid JRMSUserID
     * @param cid JRMSChannelID
     */
    private void doJoinChan(JRMSUserID uid, JRMSChannelID cid) {
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).userAddedToChannel(uid, cid);
      }
    }

    /**
     * doDeleteUser
     *
     * @param uid JRMSUserID
     */
    private void doDeleteUser(JRMSUserID uid) {
      removeUserFromManagerRecord(uid);
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).userRemoved(uid);
      }
    }

    /**
     * doNewUser
     *
     * @param uid JRMSUserID
     */
    private void doNewUser(JRMSUserID uid) {
      addUserToManagerRecord(uid);
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).userAdded(uid);
      }
    }

    /**
     * doServerPkt
     *
     * @param uid JRMSUserID
     * @param buff byte[]
     */

    private void doServerPkt(JRMSUserID user, byte[] buff) {
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).serverPacketArrived(user, buff);
      }

    }

    private void doUserPkt(JRMSUserID from, JRMSUserID to, byte[] buff) {
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).userPacketArrived(from, to, buff);
      }
    }

    private void doChannelPkt(JRMSUserID from, JRMSChannelID cid, byte[] buff) {
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (JRMSCtrlListener) i.next()).channelPacketArrived(cid, from, buff);
      }

    }

    /**
     * doHeartbeatRecieved
     *
     * @param hbguid UUID
     */

    private void doHeartbeatRecieved(UUID mid) {
      if (mid == mgrID) { // just us
        return;
      }
      boolean newone = false;
      synchronized (managerRecords) {
        ManagerRecord rec = (ManagerRecord) managerRecords.get(mid);
        if (rec == null) { // new peer
          cmgr.send(hbpacket); // send an immediate heartbeat so it knows we exist
        }
        else {
          rec.lastHBtime = System.currentTimeMillis();
        }
      }
    }

    public void socketClosed(LRMPSocketManager mgr) {
    }

  }
