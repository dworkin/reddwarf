/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;

import com.sun.sgs.benchmark.app.BehaviorModule;
import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * TODO
 */
public class CpuIntensiveModule extends AbstractModuleImpl implements Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;
    
    /** Empty constructor */
    public CpuIntensiveModule() { }
    
    // implement AbstractModuleImpl
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        Long duration = null;
        
        initVars(new Object[] { duration }, new Class<?>[] { Long.class },
            args, 1);
        
        return createOperations(duration.longValue());
    }
    
    /*
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        long duration = in.readLong();
        return createOperations(duration);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final long duration) {
        List<Runnable> operations = new LinkedList<Runnable>();
	
	operations.add(new Runnable() {
		public void run() {
		    long startTime = System.currentTimeMillis();
		    long stopTime = startTime + duration;
                    double junk = 0.0;
                    
                    if (BehaviorModule.ENABLE_INFO_OUTPUT)
                        System.out.printf("%s: running for %d ms.\n",
                            "CpuIntensiveModule", duration);
                    
                    while (System.currentTimeMillis() < stopTime) {
                        junk += Math.random();
                    }
		}
	    });
	return operations;
    }
}
