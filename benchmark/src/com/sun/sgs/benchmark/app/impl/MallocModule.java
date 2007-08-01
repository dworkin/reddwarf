/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;

import com.sun.sgs.benchmark.app.BehaviorModule;
import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * TODO
 */
public class MallocModule extends AbstractModuleImpl implements Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;
    
    /** Empty constructor */
    public MallocModule() { }
    
    // implement AbstractModuleImpl
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        Class<?> clazz = null;
	Integer count = null;
	
        initVars(new Object[] { clazz, count },
            new Class[] { Class.class, Integer.class }, args, 2);
        
        return createOperations(clazz, count.intValue());
    }
    
    /*
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        String className = (String)in.readObject();
        Class<?> clazz = Class.forName(className);
        int count = in.readInt();
        return createOperations(clazz, count);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final Class<?> clazz,
        final int count)
    {
        List<Runnable> operations = new LinkedList<Runnable>();
        
	operations.add(new Runnable() {
		public void run() {
                    if (clazz.isArray()) {
                        Object arr = Array.newInstance(clazz.getComponentType(),
                            count);
                        if (BehaviorModule.ENABLE_INFO_OUTPUT)
                            System.out.printf("%s: Creating array %s[%d].\n",
                                getClass().getName(),
                                clazz.getComponentType().getName(), count);
                    } else {
                        try {
                            // keep a pointer so they all stay in memory at once
                            Object[] arr = new Object[count];
                            for (int i = 0; i < count; i++) {
                                Constructor<?> c = 
                                    clazz.getConstructor(new Class<?>[0]);
                                arr[i] = c.newInstance(new Object[0]);
                            }
                            if (BehaviorModule.ENABLE_INFO_OUTPUT)
                                System.out.printf("%s: Creating %d instances " +
                                    "of %s.\n", getClass().getName(), count,
                                    clazz.getName());
                        } catch (IllegalAccessException iae) {
                            System.err.println("**Error in " + this + ": " + iae);
                        } catch (InstantiationException ie) {
                            System.err.println("**Error in " + this + ": " + ie);
                        } catch (InvocationTargetException ite) {
                            System.err.println("**Error in " + this + ": " + ite);
                        } catch (NoSuchMethodException nsme) {
                            System.err.println("**Error in " + this + ": " +
                                clazz.getName() + " does not provide a nullary" +
                                " constructor.");
                        }
                    }
		}
	    });
	return operations;
    }
}
