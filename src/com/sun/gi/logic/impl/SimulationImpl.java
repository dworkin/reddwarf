package com.sun.gi.logic.impl;

import java.io.*;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.logic.*;
import com.sun.gi.objectstore.*;
import com.sun.multicast.util.*;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SimulationImpl
    implements Simulation {
  SimKernel kernel;
  Router router;
  long appID;
  ClassLoader loader;
  private List userListeners = new ArrayList();
  private String appName;
  private Map userDataListeners = new HashMap();

  public SimulationImpl(SimKernel kernel, Router router, DeploymentRec game) throws InstantiationException {  
    this.kernel = kernel;
    this.appName = game.getName();
    this.router = router;
    try {
		loader = new URLClassLoader(new URL[] {new URL(game.getClasspathURL())});
	} catch (MalformedURLException e) {		
		e.printStackTrace();
		throw new InstantiationException(e.getMessage());
	}
    Class bootclass=null;
	try {
		bootclass = loader.loadClass(game.getBootClass());
	} catch (ClassNotFoundException e) {		
		e.printStackTrace();
		throw new InstantiationException(e.getMessage());
	}
    Method startMethod=null;
    try {
      startMethod = bootclass.getMethod("boot", new Class[] {SimTask.class});
    }
    catch (NoSuchMethodException ex) {
      throw new InstantiationException("Boot class in sim has no method: void boot(SimTask)");
    }
    if (appID == -1) {
      System.err.println("ERROR: Sim BOOT class must define: " +
                         "public static long SIMID = <n> where <n> >= 0");
      return;
    }
    this.appID = game.getID();
    // check for boot object. it it doesnt exist, then create it
    Transaction trans = kernel.getOstore().newTransaction(appID,
        bootclass.getClassLoader());
    long bootObjectID = trans.lookup("BOOT");
    if (bootObjectID == ObjectStore.INVALID_ID) { // doesnt exist
      try {
        bootObjectID = trans.create( (Serializable) bootclass.newInstance(),
                                    "BOOT");
      }
      catch (IllegalAccessException ex1) {
        ex1.printStackTrace();
        throw new InstantiationException(ex1.getMessage());
      }      
    }
    trans.commit();
    loader = bootclass.getClassLoader();
    queueTask(newTask(bootObjectID, startMethod, new Object[] {}));
  }

  /**
 * @param task
 */
private void queueTask(SimTask task) {
	// TODO Auto-generated method stub
	
}

/**
   * addUserListener
   *
   * @param ref SOReference
   */
  public void addUserListener(SOReference ref) {
    userListeners.add(new Long( ( (SOReferenceImpl) ref).objID));
  }

  // internal
  public SimTask newTask(long startObject, Method startMethod, Object[] params) {
    return new SimTaskImpl(this, loader, kernel.newTransaction(appID, loader),
                           startObject, startMethod, params);
  }

  // external
  public SimTask newTask(SOReference ref, String methodName, Object[] params) {
    throw new UnimplementedOperationException("not implemented yet");

  }

  /**
   * userAdded
   *
   * @param id UserID
   */
  public void userLoggedIn(UserID id, byte[] data) {
    try {
      Method userJoinedMethod =
          loader.loadClass("com.sun.gi.logic.SimUserListener").
          getMethod("userJoined", new Class[] {SimTask.class, UserID.class,
                    byte[].class});
      Object[] params = {id,data};
      for (Iterator i = userListeners.iterator(); i.hasNext(); ) {
        long objID = ( (Long) i.next()).longValue();
        queueTask(newTask(objID, userJoinedMethod, params));
      }
    }
    catch (ClassNotFoundException ex) {
      ex.printStackTrace();
    }
    catch (SecurityException ex) {
      ex.printStackTrace();
    }
    catch (NoSuchMethodException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * userRemoved
   *
   * @param id UserID
   */
  public void userLoggedOut(UserID id) {
    try {
      Method userJoinedMethod =
          loader.loadClass("com.sun.gi.logic.SimUserListener").
          getMethod("userLeft", new Class[] {SimTask.class, UserID.class});
      Object[] params = {
          id};
      for (Iterator i = userListeners.iterator(); i.hasNext(); ) {
        long objID = ( (Long) i.next()).longValue();
        queueTask(newTask(objID, userJoinedMethod, params));
      }
    }
    catch (ClassNotFoundException ex) {
      ex.printStackTrace();
    }
    catch (SecurityException ex) {
      ex.printStackTrace();
    }
    catch (NoSuchMethodException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * getAppID
   *
   * @return long
   */
  public long getAppID() {
    return appID;
  }

  /**
   * getName
   * @deprecated
   * @return String
   */
  public String getName() {
    return appName;
  }

  /**
   * channelDataArrived
   *
   * @param cid ChannelID
   * @param from UserID
   * @param data byte[]
   */
  public void receivedHostData(UserID from, byte[] data) {
    try {
      Method userJoinedMethod =
          loader.loadClass("com.sun.gi.logic.SimUserDataListener").
          getMethod("userDataReceived", new Class[] {SimTask.class,
                    UserID.class, byte[].class});
      Object[] params = {
          from, data};
      List listeners = (List) userDataListeners.get(from);
      for (Iterator i = listeners.iterator(); i.hasNext(); ) {
        long objID = ( (Long) i.next()).longValue();
        queueTask(newTask(objID, userJoinedMethod, params));
      }
    }
    catch (ClassNotFoundException ex) {
      ex.printStackTrace();
    }
    catch (SecurityException ex) {
      ex.printStackTrace();
    }
    catch (NoSuchMethodException ex) {
      ex.printStackTrace();
    }

  }

  /**
   * createUser
   */
  public UserID createUser() {
    return kernel.createUser();
  }

  /**
   * getAppName
   *
   * @return String
   */
  public String getAppName() {
    return appName;
  }

  /**
   * sendData
   *
   * @param userIDs UserID[]
   * @param userID UserID
   * @param bs byte[]
   */
  public void sendData(UserID[] targets, UserID from, byte[] bs) {
    kernel.sendData(targets, from, bs);
  }

  /**
   * addUserDataListener
   *
   * @param id UserID
   * @param ref SOReference
   */
  public void addUserDataListener(UserID id, SOReference ref) {
    List dataListeners = (List)userDataListeners.get(id);
    if (dataListeners == null) {
      dataListeners = new ArrayList();
      userDataListeners.put(id,dataListeners);
    }
    dataListeners.add(new Long( ( (SOReferenceImpl) ref).objID));
  }

}
