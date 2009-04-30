package com.sun.sgs.impl.counters;

import java.io.Serializable;

import com.sun.sgs.counters.Counter;

/**
 * A counter that does nothing. This is the lower bound we are aiming for in a
 * scalable counter. The best case is pretty much doing nothing.
 */
public class EmptyCounter extends Counter implements Serializable
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public int get()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void inc()
    {
        // TODO Auto-generated method stub
        
    }

}
