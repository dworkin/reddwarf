package com.sun.sgs.counters;

import com.sun.sgs.app.ManagedObject;

/**
 * Interface for concurrent counters.
 */
public abstract class Counter implements ManagedObject
{
    /**
     * Increments the counter value by 1.
     */
    abstract public void inc();
    
    /**
     * Retrieves the counter value.
     * @return the counter value
     */
    abstract public int get();
    
    /**
     * Successively do an inc and a get. This can be overridden to be optimized 
     * for certain counters like the AtomicCounter which supports a a swap
     * when setting the value. 
     * @return
     */
    public int incAndGet() {
        inc();
        return get();
    }
}