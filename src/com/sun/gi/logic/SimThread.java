package com.sun.gi.logic;

/**
 * <p>Title: SimThread</p>
 * <p>Description: This is the interface that describes to the rest of the GLE
 * what a SimThread looks like </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: Sun Microsystems </p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface SimThread {
  /**
   * This method is called to start a SimTask executing on this thread.
   * This method blocks if there is already a task executing on this thread.
   * It returns when the new task has begun execution.
   * @param task SimTask task for this SimThread to execute next
   */
  public void execute(SimTask task);
}
