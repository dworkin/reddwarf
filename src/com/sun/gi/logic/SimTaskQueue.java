package com.sun.gi.logic;

/**
 * <p>Title: SimTaskQueue</p>
 * <p>Description: This interface defines the APi internal to the GLE
 * for queuing tasks for execution.  It is currently a very simple single
 * FIFO though there are a numerb fo known features that coudl be added if they
 * prove necessary (prioritization, multiple queues, etc) </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Sun Microsystems, TMI </p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface SimTaskQueue {
  /**
   * This method puts a SimTask on the end of the queue for processing.
   * @param task SimTask
   */
  public void queueTask(SimTask task);
  /**
   * This is a "doit" method that transfers a thread of control to the
   * task queue for it to process the queue with.  It will start any tasks it
   * can, in FIFO order, and then return.  Those tasks will execute on a
   * seperate thread or threads according to the implementation of the
   * task queue.
   *
   */
  public void doTaskQueue();
}
