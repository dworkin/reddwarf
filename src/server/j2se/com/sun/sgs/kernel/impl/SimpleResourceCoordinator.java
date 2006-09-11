
package com.sun.sgs.kernel.impl;

import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskThread;
import com.sun.sgs.kernel.TransactionCoordinator;

import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * This is a simple implementation of <code>ResourceCoordinator</code>
 * that is provided just to help test the system as a whole.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleResourceCoordinator implements ResourceCoordinator
{

    // TEST: the queue of available threads
    private ConcurrentLinkedQueue<TaskThread> threadQueue;

    /**
     * Creates an instance of <code>SimpleResourceCoordinator</code>.
     */
    public SimpleResourceCoordinator() {
        threadQueue = new ConcurrentLinkedQueue<TaskThread>();
    }

    /**
     * FIXME: this method isn't fully implemented yet...it will simply
     * take the thread back into the pool, rather than consider scaling
     * back or doing any system notification.
     */
    public void giveThread(TaskThread taskThread) {
        threadQueue.add(taskThread);
    }

    /**
     * FIXME: this method isn't fully implemented yet...it will simply
     * return null if no threads are available, while in fact it should
     * either consider creating a new thread or throw an exception.
     */
    public TaskThread requestThread() {
        return threadQueue.poll();
    }

}
