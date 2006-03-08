package com.sun.gi.framework.interconnect.impl;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

import com.sun.gi.framework.interconnect.*;
import com.sun.gi.utils.*;
import com.sun.multicast.reliable.transport.lrmp.*;

public class LRMPTransportManager
    implements LRMPSocketListener, TransportManager {
  private static int dataPort = 6824;
  private static String addr = "224.100.100.224";
  private LRMPSocketManager cmgr;
  private static InetAddress address = null;
  private static byte[] outbytes = new byte[65];
  private DatagramPacket outpkt = new DatagramPacket(outbytes, 65);
  private ReversableMap idMap = new ReversableMap();
  private static final byte OP_CHANNEL_ANNOUNCE = 1;
  private static final byte OP_CHANNEL_REMOVE = 2;
  private static final byte OP_DATA = 3;
  private Map chanMap = new HashMap();
  private Map oldChanMap = new HashMap();
  private boolean echo=false;
private boolean TRACE=false;

  public LRMPTransportManager() {
    String prop = System.getProperty("sgs.lrmp.mcastaddress");
    if (prop != null) {
      addr = prop;
    }
    prop = System.getProperty("sgs.lrmp.mcastport");
    if (prop != null) {
      dataPort = Integer.parseInt(prop);
    }
    byte ttl = 5;
    prop =  System.getProperty("sgs.lrmp.ttl");
    if (prop != null) {
     ttl = (byte)Integer.parseInt(prop);
   }
    try {
      address = InetAddress.getByName(addr);
      LRMPTransportProfile tp = new LRMPTransportProfile(address, dataPort);
      tp.setMaxDataRate(100000);
      tp.setOrdered(true);
      tp.setTTL( ttl);
      cmgr = new LRMPSocketManager(tp);
      cmgr.setEcho(false);
      cmgr.addListener(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

  }

  /**
   * socketClosed
   *
   * @param lRMPSocketManager LRMPSocketManager
   */
  public void socketClosed(LRMPSocketManager lRMPSocketManager) {
    System.out.println("ERROR: LRMP Socket Closed!  Should never happen!");
    System.exit(300);
  }

  /**
   * packetArrived
   *
   * @param lRMPSocketManager LRMPSocketManager
   * @param inpkt DatagramPacket
   */
  public void packetArrived(LRMPSocketManager lRMPSocketManager,
                            DatagramPacket inpkt) {
    ByteBuffer buff = ByteBuffer.wrap(inpkt.getData(),
                                      inpkt.getOffset(), inpkt.getLength());    
    byte op = buff.get();
    if (TRACE){
    	System.out.println("Processing transport pkt opcode: "+op);
    }
    switch (op) {
      case OP_CHANNEL_ANNOUNCE:
        SGSUUID uuID = new StatisticalUUID();
        uuID.read(buff);
        int strsize = buff.limit() - buff.position();
        byte[] strbytes = new byte[strsize];
        buff.get(strbytes);
        String chanName = new String(strbytes);
        synchronized(idMap){
          SGSUUID oldID = (SGSUUID) idMap.reverseGet(chanName);
          boolean accept = true;
          boolean propose = false;
          if ((oldID != null) && (uuID.compareTo(oldID)>0)){
            proposeID(oldID,chanName);
          } else {
              if (oldID != null) {
                idMap.remove(oldID);
                idMap.put(uuID, chanName);
                LRMPTransportChannel currentChan =
                    (LRMPTransportChannel) chanMap.remove(oldID);
                if (currentChan != null) {
                  currentChan.uuID = uuID;
                  chanMap.put(uuID, currentChan);
                  oldChanMap.put(oldID,currentChan);
                }
            }
          }
        }
        break;
      case OP_CHANNEL_REMOVE:
        uuID = new StatisticalUUID();
        uuID.read(buff);
        LRMPTransportChannel chan = closeChannel(uuID);
        if (chan != null) {
          chan.doCloseChannel();
        }
        break;
      case OP_DATA:
        uuID = new StatisticalUUID();
        uuID.read(buff);
        chan = (LRMPTransportChannel) chanMap.get(uuID);
        if (chan == null){
          chan = (LRMPTransportChannel) oldChanMap.get(uuID);
        }
        if (chan != null) {
          ByteBuffer data = buff.slice();
          chan.doRecieveData(data);
        }
        break;
    }

  }
  
  /**
   * Closes off the channel with the given ID by removing references to it.
   * 
   * @param uuID		the channels uuID
   * 
   * @return the channel.
   */
  LRMPTransportChannel closeChannel(SGSUUID uuID) {
	  synchronized (idMap) {
		  idMap.remove(uuID);
	  }
	  LRMPTransportChannel channel = null;
	  synchronized (chanMap) {
		  channel = (LRMPTransportChannel) chanMap.remove(uuID);
	  }
	  return channel;
  }

  // Transport Manager Methods

  /**
   * openChannel
   *
   * @param channelName String
   * @return TransportChannel
   */
  public TransportChannel openChannel(String channelName) throws
      IOException {
    LRMPTransportChannel chan;
    synchronized (idMap) {
      SGSUUID chanID = (SGSUUID) idMap.reverseGet(channelName);
      if (chanID == null) {
        chanID = new StatisticalUUID();
        idMap.put(chanID, channelName);
        chan =
            new LRMPTransportChannel(channelName, chanID, this);
        chanMap.put(chanID, chan);
        proposeID(chanID, channelName);
      }
      else {
        chan = (LRMPTransportChannel) chanMap.get(chanID);
      }
    }
    return chan;
  }

  /**
   * proposeID
   *
   * @param chanID UUID
   * @param channelName String
   */
  private void proposeID(SGSUUID chanID, String channelName) {
    ByteBuffer outbuff = ByteBuffer.allocate(channelName.length()+chanID.ioByteSize()+1);
    outbuff.put(OP_CHANNEL_ANNOUNCE);
    chanID.write(outbuff);
    outbuff.put(channelName.getBytes());
    cmgr.send(new DatagramPacket(outbuff.array(),outbuff.array().length));
  }

  /**
   * ensurePacketSize
   *
   * @param outpkt DatagramPacket
   * @param i int
   */
  private DatagramPacket ensurePacketSize(DatagramPacket pkt, int i) {
    if (pkt.getData().length < i) {
      byte[] newbytes = new byte[i];
      pkt = new DatagramPacket(newbytes, i);
    }
    return pkt;
  }



  // for use by LRMPTransportChannel
  void sendData(SGSUUID uuid, ByteBuffer data) throws IOException {
    data.flip();
    int sz = data.remaining() + uuid.ioByteSize()+1;
    ByteBuffer outbuff = ByteBuffer.allocate(sz);    
    outbuff.put(OP_DATA);
    uuid.write(outbuff);
    outbuff.put(data);
    cmgr.send(new DatagramPacket(outbuff.array(), sz));
  }

  /**
   * sendData
   *
   * @param uuID UUID
   * @param byteBuffers ByteBuffer[]
   */
  public void sendData(SGSUUID uuID, ByteBuffer[] byteBuffers) {
    int sz = uuID.ioByteSize()+1;
    for(int i=0;i<byteBuffers.length;i++){
      byteBuffers[i].flip();
      sz += byteBuffers[i].remaining();
    }
    ByteBuffer outbuff = ByteBuffer.allocate(sz);
    outbuff.put(OP_DATA);
    uuID.write(outbuff);
    for(int i=0;i<byteBuffers.length;i++){
      outbuff.put(byteBuffers[i]);
    }
    cmgr.send(new DatagramPacket(outbuff.array(), sz));
  }
}
