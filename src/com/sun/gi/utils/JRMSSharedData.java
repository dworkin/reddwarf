package com.sun.gi.utils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.*;
import java.util.Set;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class JRMSSharedData implements SharedData, JRMSSharedObjectBase {
  private JRMSSharedDataManager mgr;
  private JRMSSharedMutex mutex;
  private Serializable value;
  private String name;
  private boolean initialized = false;

  public JRMSSharedData(JRMSSharedDataManager mgr, String varname) {
    this.mgr = mgr;
    this.name = varname;
    mutex = (JRMSSharedMutex) mgr.getSharedMutex(this.name+"_MUTEX");
    initialized = false;
  }

  public synchronized void initialize() {
    //System.out.println("JRMSSharedData.initialize");
    // create packet data
    try {
        long start = System.currentTimeMillis();
      // get starting value
      mgr.requestData(name);
      while (!initialized){
        Set roster = mgr.getRoster();
        if (roster.size() == 0) { // we're first
          value = null;
          initialized = true;
        } else {
          try {
            wait(mgr.getRosterTimeout());
          } catch (Exception e){
            e.printStackTrace();
          }
          if (System.currentTimeMillis()-start>=mgr.getRosterTimeout()) {
            // noone else has one of these
            value = null;
            initialized = true;
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void lock() {
    mutex.lock();
  }

  public Serializable getValue() {
    return value;
  }

  public void release() {
    mutex.release();
  }

  public void setValue(Serializable newvalue) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(newvalue);
      oos.flush();
      byte[] buff = baos.toByteArray();
      mgr.sendData(name,buff);
      ByteArrayInputStream bais = new ByteArrayInputStream(buff);
      ObjectInputStream ois = new ObjectInputStream(bais);
      value = (Serializable)ois.readObject();
      ois.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  private synchronized void sendCurrentValue() {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(value);
      oos.flush();
      byte[] buff = baos.toByteArray();
      oos.close();
      mgr.sendData(name,buff);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void dataRequest(SGSUUID uuid) {
    //System.out.println("Data requested, sending data.");
    sendCurrentValue();
  }

  public synchronized void dataAssertion(SGSUUID uuid, byte[] data) {
    //System.out.println("Recieved data assertion");
    if (mutex.getState() == JRMSSharedMutex.STATE_LOCKED) { // we have our own idea of the value
      return;
    }
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      ObjectInputStream ois = new ObjectInputStream(bais);
      value = (Serializable) ois.readObject();
      //System.out.println("asserted value = "+value);
      ois.close();
      if (!initialized){
        initialized = true;
        notifyAll();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void lockAck(SGSUUID uuid) {
     System.err.println("ERROR:  Data recieved a mutex ack!");
  }

  public void lockNak(SGSUUID uuid) {
     System.err.println("ERROR:  Data recieved a mutex nak!");
  }

  public void lockReq(SGSUUID uuid) {
     System.err.println("ERROR:  Data recieved a mutex req!");
  }

  public void lockRelease(SGSUUID uuid) {
     System.err.println("ERROR:  Data recieved a mutex release!");
  }
}