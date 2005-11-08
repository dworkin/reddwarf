package com.sun.gi.logic.impl;

import com.sun.gi.objectstore.Transaction;
import java.lang.reflect.Method;
import java.io.Serializable;
import java.lang.reflect.*;
import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SOReference;
import com.sun.gi.logic.Simulation;
import com.sun.gi.comm.routing.UserID;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

class OutputRecord {
  UserID[] targets;
  UserID uid;
  byte[] data;

  public OutputRecord(UserID[] to, UserID userID,byte[] buff){
    uid = userID;
    data = new byte[buff.length];
    System.arraycopy(buff,0,data,0,buff.length);
    targets = new UserID[to.length];
    System.arraycopy(to,0,targets,0,to.length);
  }
}

public class SimTaskImpl implements SimTask {
  private Transaction trans ;
  private SOReference startObject;
  private Method startMethod;
  private Object[] startArgs;
  private ClassLoader loader;
  private Simulation simulation;
  private List outputList = new ArrayList();

  public SimTaskImpl(Simulation sim, ClassLoader loader,
                     Transaction trans, long startObjectID,
                     Method startMethod, Object[] startArgs) {
    this.startObject = this.makeReference(startObjectID);
    this.startMethod = startMethod;
    this.trans = trans;
    this.simulation = sim;
    Object newargs[] = new Object[startArgs.length+1];
    newargs[0] = this;
    System.arraycopy(startArgs,0,newargs,1,startArgs.length);
    this.startArgs = newargs;
    this.loader = loader;
  }

  public boolean execute() {
      Serializable runobj =  startObject.get(this);
      outputList.clear();
      try {
        startMethod.invoke(runobj, startArgs);
        doOutput();
        trans.commit();
      }
      catch (InvocationTargetException ex) {
        ex.printStackTrace();
        trans.abort();
      }
      catch (IllegalArgumentException ex) {
        ex.printStackTrace();
        trans.abort();
      }
      catch (IllegalAccessException ex) {
        ex.printStackTrace();
        trans.abort();
      } catch (DeadlockException de) {
        outputList.clear();
        return false; // needs to be rescheduled
      }
      outputList.clear();
      return true; // don't reschedule, finished or fatal error
  }

  /**
   * doOutput
   */
  private void doOutput() {
    for(Iterator i = outputList.iterator();i.hasNext();){
      OutputRecord rec = (OutputRecord)i.next();
      simulation.sendData(rec.targets,rec.uid,rec.data);
    }
  }

  public SOReference makeReference(long id) {
    return new SOReferenceImpl(id);
  }

  public Transaction getTransaction() {
    return trans;
  }


  public long getAppID() {
    return trans.getCurrentAppID();
  }

  /**
   * addUserListener
   *
   * @param ref SOReference
   * @return long
   */
  public void addUserListener(SOReference ref) {
    simulation.addUserListener(ref);
  }

  /**
   * findSO
   *
   * @param soName String
   * @return SOReference
   */
  public SOReference findSO(String soName) {
    return makeReference(trans.lookup(soName));
  }


  /**
   * sendData
   *
   * @param cid ChannelID
   * @param from UserID
   * @param bs byte[]
   */
  public void sendData(UserID[] to, UserID from, byte[] bs) {
    outputList.add(new OutputRecord(to,from,bs));
  }

  /**
   * createUser
   *
   * @return UserID
   */
  public UserID createUser() {
    return simulation.createUser();
  }

  /**
   * addUserDataListener
   *
   * @param ref SOReference
   */
  public void addUserDataListener(UserID user, SOReference ref) {
    simulation.addUserDataListener(user, ref);
  }

  /**
   * createSO
   *
   * @param wurmPlayer WurmPlayer
   * @return SOReference
   */
  public SOReference createSO(Serializable simObject,String name) {
    return makeReference(trans.create(simObject,name));
  }

  
}
