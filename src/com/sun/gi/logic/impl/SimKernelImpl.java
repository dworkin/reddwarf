package com.sun.gi.logic.impl;

import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.ObjectStore;
import java.util.List;
import java.util.LinkedList;
import com.sun.gi.objectstore.impl.TTObjectStore;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.util.Set;
import java.util.Iterator;
import java.util.Map.Entry;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimThread;
import com.sun.gi.logic.impl.SimThreadImpl;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.SimFinder;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTaskQueue;
import com.sun.gi.comm.routing.nohb.NOHBRouter;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.logic.SOReference;
import java.util.ArrayList;
import com.sun.gi.comm.routing.UserID;
import com.sun.multicast.util.UnimplementedOperationException;

// data classes
class UserListenerRecord {
  long appID;
  long objID;
  public void UserListenerRecord(long appID,SOReference ref){
    this.appID = appID;
    this.objID = ((SOReferenceImpl)ref).objID;
  }
}

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SimKernelImpl
    implements SimKernel {
  private ObjectStore ostore;

  private Map appMap = new HashMap();
  private boolean running = true;
  private SimTaskQueue queue = new SimpleTaskQueue(this);
  private Router router;
  private Map simMap = new HashMap();
  private Map openChannels = new HashMap();
  private SimFinder simFinder;

  public SimKernelImpl(ObjectStore ostore, SimFinder simFinder,
                       Router router) {
    this.ostore = ostore;
    this.router = router;
    this.simFinder = simFinder;
    // now find all installed apps
    appMap = simFinder.getAllSims();
    Set appEntries = appMap.entrySet();
    for (Iterator i = appEntries.iterator(); i.hasNext(); ) {
      Entry entry = (Entry) i.next();
      long appID = ( (Long) entry.getKey()).longValue();
      Class bootClass = (Class) entry.getValue();
      String appName = simFinder.getAppName(appID);
      SimulationImpl newsim =
          new SimulationImpl(this,appName,appID,bootClass);
      /** needs to be repalced wiht real user managre code
      try {
        new GSUserManager(appName, "localhost:" + (1138 + appID),
                          router).addUserManagerListener(newsim);
      }
      catch (InstantiationException ex) {
        ex.printStackTrace();
      }
      //router.addUserListener(newsim);
**/
      simMap.put(new Long(appID),newsim);
    }
    while (running) {
      queue.doTaskQueue();
    }
  }

  public Transaction newTransaction(long appID, ClassLoader loader) {
    return ostore.newTransaction(appID, loader);
  }

  public SimThread getSimThread() {
    SimThread st = new SimThreadImpl(this);
    return st;
  }

  public ObjectStore getOstore() {
    return ostore;
  }

  public void queueTask(SimTask simTask) {
    queue.queueTask(simTask);
  }


  // main to start server
  public static void main(String[] args) {
    String installfile;

    if (args.length == 0) {
      installfile = System.getProperty("user.dir") + File.separator +
          "siminstall.txt";
    }
    else {
      installfile = args[0];
    }
    try {
      Router router =  new NOHBRouter();
      SimKernelImpl simKernelImpl1 = new SimKernelImpl(new TTObjectStore(),
          new InstallFileSimFinder(new File(installfile)),router);
      // start proxy server
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }







  /**
   * createUser
   */
  public UserID createUser() {
    //return router.createUser();
    throw new UnimplementedOperationException();
  }

  /**
   * sendData
   *
   * @param targets UserID[]
   * @param from UserID
   * @param bs byte[]
   */
  public void sendData(UserID[] targets, UserID from, byte[] bs) {
    //router.broadcastData(targets,from,bs);
     throw new UnimplementedOperationException();
  }

}
