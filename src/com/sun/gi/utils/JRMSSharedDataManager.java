package com.sun.gi.utils;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */
import com.sun.multicast.reliable.transport.*;
import com.sun.multicast.reliable.transport.lrmp.*;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.Channel;
import java.net.InetAddress;
import com.sun.gi.utils.UUID;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.*;
import java.util.*;
import java.io.Serializable;
import java.util.Collection;

public class JRMSSharedDataManager
    implements JRMSChannelRosterManagerListener, SharedDataManager {
  private static InetAddress address = null;
  private static int dataPort = 6824;
  private static String addr = "224.100.100.224";
  private PrimaryChannelManager pcm;
  private Channel channel;
  private UUID guid;
  private JRMSChannelRosterManager rosterManager;
  // op codes
  static final int OP_LOCK_REQ = 0;
  static final int OP_LOCK_ACK = 1;
  static final int OP_LOCK_NAK = 2;
  static final int OP_LOCK_RELEASE = 3;
  static final int OP_DATA_REQUEST = 4;
  static final int OP_DATA_ASSERT = 5;

  private Map dataMap = new HashMap();
  public JRMSSharedDataManager() {
    guid = new StatisticalUUID();
    try {
      LRMPTransportProfile tp;
      address = InetAddress.getByName(addr);

      tp = new LRMPTransportProfile(address, dataPort);
      tp.setMaxDataRate(100000);
      pcm = ChannelManagerFinder.getPrimaryChannelManager(null);
      channel = pcm.createChannel();
      channel.setChannelName("SGS Shared Data Channel");
      channel.setApplicationName("SGS");
      channel.setTransportProfile(tp);
      channel.setAbstract("Used for coordinating shared data.");
      channel.setAdvertisingRequested(true);
      rosterManager = new JRMSChannelRosterManager(channel);
      rosterManager.addListener(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public SharedMutex getSharedMutex(String name) {
    JRMSSharedMutex mutex = new JRMSSharedMutex(this, name);
    dataMap.put(name, mutex);
    return mutex;
  }

  public void pktArrived(UUID uuid, byte[] buff) {
    int strlen = 0;
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(buff);
      DataInputStream dis = new DataInputStream(bais);
      strlen = dis.readInt();
      byte[] strbuff = new byte[strlen];
      dis.read(strbuff);
      String name = new String(strbuff);
      JRMSSharedObjectBase obj = (JRMSSharedObjectBase) dataMap.get(name);
      int op = dis.readByte();
      //System.out.println("recieved op = "+op);
      switch (op) {
        case OP_LOCK_REQ:
          if (obj == null) { // we dont have this lock so ACK it
            sendLockAck(name);
          }
          else {
            obj.lockReq(uuid);
          }
          break;
        case OP_LOCK_ACK:
          if (obj != null) {
            obj.lockAck(uuid);
          }
          break;
        case OP_LOCK_NAK:
          if (obj != null) {
            obj.lockNak(uuid);
          }
          break;
        case OP_LOCK_RELEASE:
          if (obj != null) {
            obj.lockRelease(uuid);
          }
          break;
        case OP_DATA_REQUEST:
          if (obj != null) {
            obj.dataRequest(uuid);
          }
          break;
        case OP_DATA_ASSERT:
          if (obj != null) {
            byte[] databuff = new byte[dis.readInt()];
            dis.read(databuff);
            obj.dataAssertion(uuid, databuff);
          }
          break;
      }
      dis.close();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public UUID getUUID() {
    return guid;
  }

  public SharedData getSharedData(String name) {
    name = "__SHARED_"+name;
    JRMSSharedData sdata = new JRMSSharedData(this, name);
    dataMap.put(name, sdata);
    sdata.initialize();
    return sdata;
  }

  public void sendData(String name, byte[] buff) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeInt(name.length());
      dos.writeBytes(name);
      dos.writeByte(JRMSSharedDataManager.OP_DATA_ASSERT);
      dos.writeInt(buff.length);
      dos.write(buff);
      dos.flush();
      byte[] outbuff = baos.toByteArray();
      dos.close();
      rosterManager.sendData(outbuff);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void sendLockReq(String name) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeInt(name.length());
      dos.writeBytes(name);
      dos.writeByte(OP_LOCK_REQ);
      dos.flush();
      byte[] outbuff = baos.toByteArray();
      dos.close();
      rosterManager.sendData(outbuff);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void sendLockNak(String name) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeInt(name.length());
      dos.writeBytes(name);
      dos.writeByte(OP_LOCK_NAK);
      dos.flush();
      byte[] outbuff = baos.toByteArray();
      dos.close();
      rosterManager.sendData(outbuff);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void sendLockAck(String name) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeInt(name.length());
      dos.writeBytes(name);
      dos.writeByte(OP_LOCK_ACK);
      dos.flush();
      byte[] outbuff = baos.toByteArray();
      dos.close();
      rosterManager.sendData(outbuff);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void sendLockRelease(String name) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeInt(name.length());
      dos.writeBytes(name);
      dos.writeByte(OP_LOCK_RELEASE);
      dos.flush();
      byte[] outbuff = baos.toByteArray();
      dos.close();
      rosterManager.sendData(outbuff);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Set getRoster() {
    return rosterManager.getRoster();
  }

  public long getRosterTimeout() {
    return rosterManager.HEARTBEATMILLIS;
  }

  public void requestData(String name) {
    //System.out.println("Sending data request.");
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeInt(name.length());
      dos.writeBytes(name);
      dos.writeByte(OP_DATA_REQUEST);
      dos.flush();
      byte[] outbuff = baos.toByteArray();
      dos.close();
      rosterManager.sendData(outbuff);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
