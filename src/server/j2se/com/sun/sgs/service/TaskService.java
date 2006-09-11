
package com.sun.sgs.service;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.ManagedRunnable;

import com.sun.sgs.kernel.TaskQueue;


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
     * Tells this service about the <code>TaskQueue</code> where future
     * tasks get queued.
     *
     * @param taskQueue the system's task queue
     */
    public void setTaskQueue(TaskQueue taskQueue);

    /**
     * Queues a task to run.
     *
     * @param task the task to run
     */
    public void queueTask(ManagedReference<? extends ManagedRunnable> task);

}
