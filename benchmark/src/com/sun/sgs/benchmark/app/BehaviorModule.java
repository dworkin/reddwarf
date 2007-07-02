package com.sun.sgs.benchmark.app;

import java.util.List;

import com.sun.sgs.app.Task;

/**
 * A loadable module for generating tasks based on a predefined
 * behavior.
 */
public interface BehaviorModule {
    
    /**
     * Returns an ordered set of operations to perform for this
     * module.  
     *
     * @param args op-codes denoting the arguments
     *
     * @return an ordered set of operations to perform for this
     *         module.
     */
    public List<Runnable> getOperations(byte[] args);

    /**
     * Returns an ordered set of operations to perform for this
     * module.  
     *
     * @param args Objects arguments to be invoked for this task
     *
     * @return an ordered set of operations to perform for this
     *         module.
     */
    public List<Runnable> getOperations(Object[] args);


}