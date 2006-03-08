package com.sun.gi.utils;

import com.sun.gi.utils.SGSUUID;
import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.transport.*;
import com.sun.gi.utils.StatisticalUUID;
import com.sun.multicast.util.*;
import java.io.*;
import com.sun.multicast.reliable.*;
import java.net.DatagramPacket;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;
import java.net.InetAddress;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class JRMSChannelRosterManager {
  private SGSUUID myUUID;
  private RMPacketSocket ps;
  static final int HEARTBEATMILLIS = 500;
  private static final byte OP_HEARTBEAT = 0;
  private static final byte OP_DATAPKT = 1;

  private Channel channel = null;
  private Map roster = new HashMap();

  private List listeners = new ArrayList();
  public JRMSChannelRosterManager(Channel channel) {
    this.channel = channel;
    myUUID = new StatisticalUUID();
    try {
      ps = channel.createRMPacketSocket(TransportProfile.SEND_RECEIVE);
      startListening();
      startHeartbeat();
      Thread.sleep(HEARTBEATMILLIS); // allow roster to tick once
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void startHeartbeat() {
    new Thread() {
      public void run() {
        byte[] buff = null;
        try {
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(baos);
          oos.writeByte(OP_HEARTBEAT);
          oos.writeObject(myUUID);
          oos.flush();
          buff = baos.toByteArray();
          oos.close();
          baos.close();
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        while (true) {
          DatagramPacket pkt = new DatagramPacket(buff, buff.length);
          try {
            ps.send(pkt);
            Thread.sleep(HEARTBEATMILLIS);
          }
          catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      }
    }

    .start();

  }

  private void startListening() {
    new Thread() {
      public void run() {
        while (true) {
          try {
            DatagramPacket pkt = ps.receive();
            ByteArrayInputStream bais = new ByteArrayInputStream(pkt.getData(),
                pkt.getOffset(), pkt.getLength());
            ObjectInputStream ois = new ObjectInputStream(bais);
            byte op = ois.readByte();
            switch (op) {
              case OP_HEARTBEAT:
                SGSUUID uuid = (SGSUUID) ois.readObject();
                doHeartbeat(uuid);
                break;
              case OP_DATAPKT:
                uuid = (SGSUUID) ois.readObject();
                byte[] buff = new byte[ois.available()];
                ois.read(buff, 0, buff.length);
                doData(uuid,buff);
                break;
            }
            ois.close();
            bais.close();

          }
          catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      }
    }.start();
  }

  private void doHeartbeat(SGSUUID uuid){
    roster.put(uuid,new Long(System.currentTimeMillis()));
  }

  public Set getRoster(){
    for(Iterator i = roster.entrySet().iterator();i.hasNext();){
      Map.Entry entry = (Map.Entry)i.next();
      if (((Long)entry.getValue()).longValue() + (HEARTBEATMILLIS*2) <
          System.currentTimeMillis()) { // expired
        i.remove();
      }
    }
    return roster.keySet();
  }

  public void sendData(byte[] buff){
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] outbuff = null;
    try {
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeByte(OP_DATAPKT);
      oos.writeObject(myUUID);
      oos.write(buff);
      oos.flush();
      outbuff = baos.toByteArray();
      DatagramPacket pkt = new DatagramPacket(outbuff,0,outbuff.length);
      ps.send(pkt);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public void addListener(JRMSChannelRosterManagerListener l){
    listeners.add(l);
  }

  private void doData(SGSUUID uuid, byte[] buff){
    fireDataArrived(uuid, buff);
  }

  private void fireDataArrived(SGSUUID uuid, byte[] buff) {
    for(Iterator i = listeners.iterator();i.hasNext();){
      ((JRMSChannelRosterManagerListener)i.next()).pktArrived(uuid,buff);
    }
  }

  // test routine

  private static int dataPort = 6824;
  private static String addr = "224.100.100.224";

  public static void main(String[] args){
    final Object printMutex = new Object();
    try {
      LRMPTransportProfile tp;
      InetAddress address = InetAddress.getByName(addr);
      tp = new LRMPTransportProfile(address, dataPort);
      tp.setMaxDataRate(100000);
      PrimaryChannelManager pcm = ChannelManagerFinder.getPrimaryChannelManager(null);
      Channel channel = pcm.createChannel();
      channel.setChannelName("SGS Shared Data Channel");
      channel.setApplicationName("SGS");
      channel.setTransportProfile(tp);
      channel.setAbstract("Used for coordinating shared data.");
      channel.setAdvertisingRequested(true);
      JRMSChannelRosterManager crm = new JRMSChannelRosterManager(channel);
      String intro = "Joining channel message from "+crm.myUUID+" : Hello!";
      crm.sendData(intro.getBytes());
      crm.addListener(new JRMSChannelRosterManagerListener() {
        public void pktArrived(SGSUUID uuid, byte[] buff){
          synchronized(printMutex){
            System.out.println("MSG FROM "+uuid+": "+new String(buff));
          }
        }
      });
      while(true){
        try {
          Thread.sleep(3000);
        } catch (Exception e) {
          e.printStackTrace();
        }
        Set roster = crm.getRoster();
        synchronized(printMutex){
          System.out.print("Roster :");
          for (Iterator i = roster.iterator(); i.hasNext(); ) {
            System.out.print(i.next() + " ");
          }
          System.out.println();
        }
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}