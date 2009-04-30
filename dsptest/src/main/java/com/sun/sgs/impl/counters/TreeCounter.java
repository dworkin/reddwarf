package com.sun.sgs.impl.counters;

import java.io.Serializable;

/**
 * Counter that aims for a middle ground between the {@code PoolCounter} and the
 * {@code TaskCounter}. The locking pattern looks a bit like a tree. Bigger
 * chunks of the pool is counters is locked at a time with a background task.
 * However, this allows the value to be fresher.
 */
public class TreeCounter extends PoolCounter implements Serializable
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    
    
}
