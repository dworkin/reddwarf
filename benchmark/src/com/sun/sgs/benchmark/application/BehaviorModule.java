package com.sun.sgs.benchmark.application;

import java.util.List;

import com.sun.sgs.app.Task;

/**
 * A loadable module for generating tasks based on a predefined
 * behavior.
 *
 * @author Ian Rose
 * @author David Jurgens
 */
public interface BehaviorModule {
    
    /**
     * Returns an ordered set of operations to perform for this
     * module.  These tasks should be enqueued using {@link
     * com.sun.sgs.app.TaskManager#scheduleTask)}
     *
     * @param args op-codes denoting the arguments
     *
     * @return an ordered set of operations to perform for this
     *         module.
     */
    public List<Task> getTasks(byte[] args);

    /**
     * Returns an ordered set of operations to perform for this
     * module.  These tasks should be enqueued using {@link
     * com.sun.sgs.app.TaskManager#scheduleTask)}
     *
     * @param args Objects arguments to be invoked for this task
     *
     * @return an ordered set of operations to perform for this
     *         module.
     */
    public List<Task> getTasks(Object[] args);


}