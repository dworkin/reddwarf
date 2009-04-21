/*
 * 
 */
package com.sun.sgs.impl.counters;

import java.io.Serializable;

import com.sun.sgs.app.ManagedObject;

public final class NormalInteger implements ManagedObject, Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * 
     */
    private int internalInt = 0;
    
    /**
     * 
     * @return
     */
    public int inc() {
        return ++internalInt;
    }
    
    /**
     * 
     * @param val
     */
    public void set(int val) {
        internalInt = val;
    }
    
    /**
     * 
     * @return
     */
    public int get() {
        return internalInt;
    }
}
