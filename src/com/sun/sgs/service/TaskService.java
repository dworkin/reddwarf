
package com.sun.sgs.service;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.ManagedRunnable;

import com.sun.sgs.kernel.EventQueue;


/**
 * This type of <code>Service</code> handles queuing tasks. All services
 * that need to queue a task do so through a <code>TaskService</code>.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface TaskService extends Service
{

    /**
     * Tells this service about the <code>EventQueue</code> where future
     * tasks get queued.
     *
     * @param eventQueue the system's event queue
     */
    public void setEventQueue(EventQueue eventQueue);

    /**
     * Queues a task to run.
     *
     * @param txn the transaction state
     * @param task the task to run
     */
    public void queueTask(Transaction txn,
                          ManagedReference<? extends ManagedRunnable> task);

}
