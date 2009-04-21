package com.sun.sgs.impl.counters;

import java.io.Serializable;

import com.sun.sgs.app.ManagedObjectRemoval;

public class ScalableStatCounter extends Number 
implements Serializable, ManagedObjectRemoval
{
    
    /***
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1L;
    private int i = 0;
    
    
    public void inc() {
        
    }
    
    public int get() {
        return 1;
    }
    
    @Override
    public double doubleValue()
    {
        // TODO Auto-generated method stub
        return 0;
    }
    @Override
    public float floatValue()
    {
        // TODO Auto-generated method stub
        return 0;
    }
    @Override
    public int intValue()
    {
        // TODO Auto-generated method stub
        return 0;
    }
    @Override
    public long longValue()
    {
        // TODO Auto-generated method stub
        return 0;
    }
    @Override
    public void removingObject()
    {
        // TODO Auto-generated method stub
        
    }
    
}
