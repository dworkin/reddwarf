package com.sun.gi.utils;

import java.net.*;
import java.util.*;

import com.sun.multicast.reliable.transport.*;
import com.sun.multicast.reliable.transport.lrmp.*;
import com.sun.multicast.reliable.*;
import java.io.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LRMPSocketManager
    implements Runnable {
  RMPacketSocket ps;
  List listeners = new ArrayList();

  private boolean exit = false;
  private static final boolean DEBUGPKTS = false;
  private boolean echo = false;

  /**
   * JRMSChannelManager
   *
   * @param channel Channel
   */
  public LRMPSocketManager(LRMPTransportProfile tp) {
    try {
      ps = tp.createRMPacketSocket(TransportProfile.SEND_RECEIVE);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    new Thread(this).start();
  }

  /**
   * send
   *
   * @param hbpacket DatagramPacket
   */
  public synchronized void send(DatagramPacket packet) {
    try {
      ps.send(packet);
      if (echo) {
        packetArrived(packet);
      }
      if (DEBUGPKTS){
    	  System.err.println("LRMPTRansportMgr sent packet");
      }
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * packetArrived
   *
   * @param packet DatagramPacket
   */
  private void packetArrived(DatagramPacket packet) {
    synchronized (listeners) {
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        ( (LRMPSocketListener) i.next()).packetArrived(this, packet);
      }
    }
  }

  /**
   * run
   */
  public void run() {
    while (!exit) {
      DatagramPacket inpkt = null;
      try {
        inpkt = ps.receive();
        if (DEBUGPKTS){
      	  System.err.println("LRMPTRansportMgr sent packet");
        }
        packetArrived(inpkt);        
      }
      catch (SessionDoneException ex) {
        ex.printStackTrace();
        return; // done
      }
      catch (RMException ex) {
        ex.printStackTrace();
        return;
      }
      catch (IOException ex) {
        ex.printStackTrace();
        return;
      }
    }
  }

  public void addListener(LRMPSocketListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  /**
   * dispose
   */
  public void dispose() {
    exit = true;
  }

  /**
   * setEcho
   *
   * @param b boolean
   */
  public void setEcho(boolean b) {
    echo = b;
  }

}
