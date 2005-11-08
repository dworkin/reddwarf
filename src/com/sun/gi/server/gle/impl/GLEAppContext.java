package com.sun.gi.server.gle.impl;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

import com.sun.gi.server.gle.*;
import java.awt.BorderLayout;
import com.sun.gi.utils.CommandLineWindow;
import com.sun.gi.server.ostore.ObjectStore;
import com.sun.gi.utils.CommandLineWindowListener;
import com.sun.gi.utils.types.StringUtils;
import com.sun.gi.server.ostore.Transaction;
import java.io.Serializable;
import java.util.List;
import java.util.LinkedList;

class GLERuntime implements Runnable {
  boolean done = false;
  TaskQueue taskQueue;
  Executor executor;
  public GLERuntime(TaskQueue queue, Executor exec){
    taskQueue = queue;
    executor = exec;
  }

  public void run(){
    while (!done) {
      try {
        Task nextTask = taskQueue.nextTask();
        try {
          executor.executeTask(nextTask);
        } catch (TimeStampInterrupt ts) {
          // time stamp abort, requeue task
          taskQueue.queueTask(nextTask);
        } catch (Exception ex) {
          throw ex; // propegate it
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}

public class GLEAppContext
    implements Kernel, CommandLineWindowListener {
  ObjectStore ostore;
  ClassLoader cloader;
  TaskQueue queue;

  public GLEAppContext(String appname, ObjectStore os, TaskQueue taskQueue,
                       Executor exec, ClassLoader cl) {
    ostore = os;
    cloader = cl;
    queue = taskQueue;
    new Thread(new GLERuntime(queue,exec)).start();
    CommandLineWindow clw = new CommandLineWindow(appname);
    clw.addListener(this);
    clw.show();
  }

  public void queueTask(Task task) {
    queue.queueTask(task);
  }

  public Class resolveClass(String fqcn) {
    try {
      return cloader.loadClass(fqcn);
    }
    catch (ClassNotFoundException ex) {
      ex.printStackTrace();
      return null;
    }
  }

  public Class resolveClass(String fqcn, float version) {
    return resolveClass(fqcn); // for the moment ignore version
  }

  public void lineInput(String line) {
    try {
      String[] param = StringUtils.explode(line, " ");
      if (param[0].equalsIgnoreCase("create")) {
        Class cl = resolveClass(param[1]);
        Object obj = cl.newInstance();
        Transaction trans = ostore.newTransaction(ostore.getTimestamp());
        trans.create( (Serializable) obj, param[2]);
        trans.commit();
      }
      else if (param[0].equalsIgnoreCase("task")) {
        long oid = ostore.lookup(param[1]);
        long ts = ostore.getTimestamp();
        ObjectReference oref = new GLEObjectReference(oid);
        Task task = new GLETask(this,ts,oref, param[2], null);
        queueTask(task);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

}