package com.sun.gi.logic.impl;

import com.sun.gi.logic.SimThread;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimKernel;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SimThreadImpl extends Thread implements SimThread {
  SimTask task=null;
  private boolean result;
  private SimKernel kernel;
  private boolean reused = false;

  public SimThreadImpl(SimKernel kernel) {
    super();
    this.kernel = kernel;
    this.start();
  }

  public synchronized void execute(SimTask task) {
    while(this.task != null) {
      try {
        wait();
      }
      catch (InterruptedException ex) {
        ex.printStackTrace();
      }
    }
    this.task = task;
    notifyAll();
  }

  public void run(){
    do {
      synchronized (this) {
        while (task == null) {
          try {
            wait();
          }
          catch (InterruptedException ex) {
            ex.printStackTrace();
          }
        }
      }
      if (!task.execute()) {
        kernel.queueTask(task);
      } else {

      }
      synchronized(this){
        task = null;
        notifyAll();
      }
    } while (reused);
  }
}
