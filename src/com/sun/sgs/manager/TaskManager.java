
/*
 * TaskManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Jul 10, 2006	12:41:49 AM
 * Desc: 
 *
 */


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

    /**
     * Returns an instance of <code>TaskManager</code>.
     *
     * @return an instance of <code>TaskManager</code>
     */
    public static TaskManager getInstance() {
        // FIXME: return the instance
        return null;
    }

    /**
     * Queues a task to run.
     *
     * @param taskReference the task to run
     */
    public abstract void queueTask(ManagedReference<? extends ManagedRunnable>
				   taskReference);

}
