package com.sun.sgs.benchmark.app;

import java.util.List;

import com.sun.sgs.app.ClientSession;

/**
 * A loadable module for generating tasks based on a predefined
 * behavior.
 */
public interface BehaviorModule {
    
    /**
     * Returns an ordered set of operations to perform for this
     * module.  
     *
     * @param session the client session on whose behalf this method
     *                is invoked
     * @param args op-codes denoting the arguments
     *
     * @return an ordered set of operations to perform for this
     *         module.
     */
    public List<Runnable> getOperations(ClientSession session, byte[] args)
        throws BehaviorException;
    
    /**
     * Returns an ordered set of operations to perform for this
     * module.  
     *
     * @param session the client session on whose behalf this method
     *                is invoked
     * @param args    Object arguments to be invoked for this task
     *
     * @return an ordered set of operations to perform for this
     *         module.
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException;
}
