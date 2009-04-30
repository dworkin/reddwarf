/*
 * 
 */
package com.sun.sgs.impl.counters;

import java.io.Serializable;

import com.sun.sgs.counters.Counter;

/**
 * Counter that uses a primitive "int" as it's internal counter.
 */
public class PrimitiveCounter extends Counter implements Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * 
     */
    private int internalInt;

    @Override
    public int get()
    {
        return internalInt;
    }

    @Override
    public void inc()
    {
        internalInt++;
    }
    
    public void set(int value)
    {
        internalInt = value;
    }
    
}
