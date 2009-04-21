package com.sun.ds;

import java.io.Serializable;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

public class ScalableCounter2 implements ManagedObject, Serializable
{
    /**
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1L;

    public ScalableCounter2() {
//        if(numCounters < 1) {
//            throw new IllegalArgumentException(
//                    "numCounters cannot be less than 1");
//        }
//        
//        counters = new ManagedReference[numCounters];
//        counters[0] = AppContext.getDataManager().createReference(
//                new InternalCounter(initialValue));
//        for(int i = 1; i < numCounters; i++) {
//            counters[i] = AppContext.getDataManager().createReference(
//                    new InternalCounter(0));
//        }
    }
    
    public void inc() {
        
    }
    
    public int get() {
        return 0;
    }
}
