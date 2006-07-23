

package com.sun.sgs.manager;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.ManagedRunnable;


/**
 * This manager provides access to the task-related routines.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class TaskManager
{

    // the singleton instance of TaskManager
    private static TaskManager manager = null;

    /**
     * Creates an instance of <code>TaskManager</code>. This class enforces
     * a singleton model, so only one instance of <code>TaskManager</code>
     * may exist in the system.
     *
     * @throws IllegalStateException if an instance already exists
     */
    protected TaskManager() {
        if (manager != null)
            throw new IllegalStateException("TaskManager is already " +
                                            "initialized");

        manager = this;
    }

    /**
     * Returns the instance of <code>TaskManager</code>.
     *
     * @return the instance of <code>TaskManager</code>
     */
    public static TaskManager getInstance() {
        return manager;
    }

    /**
     * Queues a task to run.
     *
     * @param taskReference the task to run
     */
    public abstract void queueTask(ManagedReference<? extends ManagedRunnable>
				   taskReference);

}
