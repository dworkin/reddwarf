package com.sun.sgs.impl.counters;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.sgs.counters.Counter;

/**
 * Counter that uses an AtomicInteger as it's internal counter. The distinction
 * between using a primitive "int" as the internal counter is that this is an
 * object and it is supported to do faster writes.
 */
public class AtomicIntegerCounter extends Counter implements Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private AtomicInteger internalInt = new AtomicInteger();
    
    @Override
    public int get()
    {
        return internalInt.get();
    }

    @Override
    public void inc()
    {
        incAndGet();
    }
    
    @Override
    public int incAndGet()
    {
        return internalInt.incrementAndGet();
    }

}