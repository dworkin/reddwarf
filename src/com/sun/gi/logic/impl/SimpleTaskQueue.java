package com.sun.gi.logic.impl;

import com.sun.gi.logic.SimTaskQueue;
import com.sun.gi.logic.SimTask;
import java.util.List;
import java.util.LinkedList;
import com.sun.gi.logic.SimThread;
import com.sun.gi.logic.SimKernel;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SimpleTaskQueue implements SimTaskQueue {
  private SimKernel kernel;
  private List taskList = new LinkedList();

  public SimpleTaskQueue(SimKernel k) {
    kernel = k;
  }

  public void doTaskQueue() {
    SimTask task;
    synchronized(taskList){
      while (taskList.size() == 0) {
        try {
          taskList.wait();
        }
        catch (InterruptedException ex) {
          // woke up for some exceptional reason
          ex.printStackTrace();
        }
      }
      task = (SimTask)taskList.remove(0);
    }
    SimThread thr = kernel.getSimThread();
    thr.execute(task);
  }

  public void queueTask(SimTask simTask) {
    synchronized(taskList){
      taskList.add(simTask);
      taskList.notifyAll();
    }
  }

}