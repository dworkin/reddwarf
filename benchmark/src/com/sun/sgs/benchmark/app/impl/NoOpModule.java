/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.ClientSession;

import com.sun.sgs.benchmark.app.BehaviorModule; 
import com.sun.sgs.benchmark.app.BehaviorException; 

/**
 * TODO
 */
public class NoOpModule implements BehaviorModule, Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;
    
    /** Empty constructor */
    public NoOpModule() { }
    
    // implement BehaviorModule
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, byte[] args)
        throws BehaviorException
    {
        if (BehaviorModule.ENABLE_INFO_OUTPUT)
            System.out.printf("%s called.\n", getClass().getSimpleName());
        return new LinkedList<Runnable>();
    }
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        if (BehaviorModule.ENABLE_INFO_OUTPUT)
            System.out.printf("%s called.\n", getClass().getSimpleName());
        return new LinkedList<Runnable>();
    }
}
