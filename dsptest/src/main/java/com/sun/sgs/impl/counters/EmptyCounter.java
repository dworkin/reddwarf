package com.sun.sgs.impl.counters;

import java.io.Serializable;

import com.sun.sgs.app.ManagedObject;

public class EmptyCounter implements ManagedObject, Serializable
{
    /**
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1L;
    
    public void inc() {
        
    }
    
    public int get() {
        return 0;
    }
}
