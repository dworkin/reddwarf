

package com.sun.sgs.app;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.ManagedRunnable;

import com.sun.sgs.kernel.TaskThread;


/**
 * This manager provides access to the task-related routines.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class TaskManager
{

    /**
     * Creates an instance of <code>TaskManager</code>.
     */
    protected TaskManager() {

    }

    /**
     * Returns the instance of <code>TaskManager</code>.
     *
     * @return the instance of <code>TaskManager</code>
     */
    public static TaskManager getInstance() {
        return ((TaskThread)(Thread.currentThread())).getTask().
            getAppContext().getTaskManager();
    }

    /**
     * Queues a task to run.
     *
     * @param taskReference the task to run
     */
    public abstract void queueTask(ManagedReference<? extends ManagedRunnable>
				   taskReference);

}
