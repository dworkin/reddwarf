package com.sun.gi.utils.test;

import com.sun.gi.utils.DistributedMutexMgr;
import com.sun.gi.utils.DistributedMutex;
import com.sun.gi.utils.DistributedMutexMgrImpl;
import com.sun.gi.utils.ReliableMulticasterImpl;
import java.net.InetAddress;
import com.sun.multicast.reliable.*;
import java.io.*;
import com.sun.gi.utils.DistributedMutexMgrListener;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LockSpammer implements DistributedMutexMgrListener, Runnable {
  static long counter=0;
  DistributedMutexMgr mgr;
  static DistributedMutex mutex = null;
  String procid;
  boolean failover = false;

  public LockSpammer(String procid, boolean failover) {
    this.procid = procid;
    this.failover = failover;
    if (mutex == null) {
      try {
        mgr = new DistributedMutexMgrImpl(
            new ReliableMulticasterImpl(InetAddress.getByName("224.0.0.1"),
                                        9999, null));
        mgr.addListener(this);
        mutex = mgr.getMutex("LockSpammer");
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  public void run() {
    byte[] buff = new byte[4];
    while(true){
      if (failover) { // wait til we have been introduced
        while (!mgr.hasPeers()){
          try {
            Thread.sleep(100);
          } catch (Exception e){
            e.printStackTrace();
          }
        }
      }
      try {
        mutex.lock();
      }
      catch (InterruptedException ex) {
        ex.printStackTrace();
      }
      System.out.println("Got Lock: "+System.currentTimeMillis());
      if (failover) {
        System.out.println("Quitting while locked (failover test.)");
        System.exit(0);
      }
      counter++;
      System.out.println("Set counter to: "+counter);
      buff[0] = (byte)((counter>>24)&0xFF);
      buff[1] = (byte)((counter>>16)&0xFF);
      buff[2] = (byte)((counter>>8)&0xFF);
      buff[3] = (byte)((counter)&0xFF);
      mgr.sendData(buff);
      //System.out.println("("+procid+") Sent: "+counter);
      mutex.release();
      System.out.println("Released Lock: "+System.currentTimeMillis());
      try {
        Thread.sleep(10);
      } catch (Exception e){
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args){
    if (args.length == 0){
       new Thread(new LockSpammer("1",false)).start();
    } else {
      new Thread(new LockSpammer("1", args[0].equals("-failover"))).start();
    }
    //new Thread(new LockSpammer("2")).start();
 }

  public void receiveData(byte[] buff) {
    long newcount = (((((long)(buff[0]))&0xff)<<24)|
                     ((((long)(buff[1]))&0xff)<<16)|
                     ((((long)(buff[2]))&0xff)<<8)|
                     ((((long)(buff[3]))&0xff)));
    if (newcount>=counter) {
      counter = newcount;
      System.out.println("Received counter =  "+counter);
    }
  }
}