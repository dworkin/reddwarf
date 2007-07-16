/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.util.Arrays;
import java.util.List;

import com.sun.sgs.app.ClientSession;

import com.sun.sgs.benchmark.app.BehaviorModule;
import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * Provides some basic, useful functionality that most {@code BehaviorModule}
 * implementations will want to make use of.
 */
public abstract class AbstractModuleImpl implements BehaviorModule {
    
    /** Empty constructor */
    protected AbstractModuleImpl() { }
    
    /**
     * Presents the method arguments as an ObjectInputStream; implementations do
     * not need to do any error checking for underflow or overflow of the stream
     * as that will be handled by the caller (hence, this method allows
     * implementations to throw {@code EOFException} and {@code IOException}).
     */
    protected abstract List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException;
    
    /**
     * Casts the objects in {@code args} to the types in {@code types} and
     * assigns them to {@code vars}.
     */
    protected void initVars(Object[] vars, Class<?>[] types, Object[] args)
        throws BehaviorException
    {
        initVars(vars, types, args, vars.length);
    }
    
    /**
     * Casts the objects in {@code args} to the types in {@code types} and
     * assigns them to {@code vars}.
     */
    protected void initVars(Object[] vars, Class<?>[] types, Object[] args,
        int requiredArgs)
        throws BehaviorException
    {
        if (vars.length != types.length)
            throw new IllegalArgumentException("Size of vars[] (" + vars.length +
                ") must match size of types[] (" + types.length + ").");
        
        String expectedArgNum;
        
        if (requiredArgs == vars.length)
            expectedArgNum = "" + requiredArgs;
        else
            expectedArgNum = requiredArgs + "-" + vars.length;
        
        if ((args.length < requiredArgs) || (args.length > vars.length))
            throw new BehaviorException("Invalid number of parameters to " +
                this + ": expected " + expectedArgNum + ", got: " + args.length +
                " (" + Arrays.toString(args) + ").");
        
        for (int i=0; i < vars.length; i++) {
            try {
                vars[i] = types[i].cast(args[i]);
            }
            catch (ClassCastException cce) {
                throw new BehaviorException("Invalid parameter #" + i + " to " +
                    this + ": " + args[i] + ". Expected " + types[i], cce);
            }
        }
    }
    
    // implement BehaviorModule
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, byte[] args)
        throws BehaviorException
    {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(args);
            ObjectInputStream ois = new ObjectInputStream(bais);
            
            List<Runnable> operations = getOperations(session, ois);
            
            if (ois.available() == 0) {  /** Good, nothing left */
                ois.close();
                return operations;
            } else {  /** Too many parameters passed? */
                throw new BehaviorException("invalid parameter(s) to " +
                    this + ": too many bytes");
            }
        }
        catch (ClassNotFoundException cnfe) {
            throw new BehaviorException(cnfe);
        }
        catch (EOFException eofe) {
            throw new BehaviorException("invalid parameter(s) to " +
                this + ": not enough bytes");
        }
        catch (IOException ioe) {
            throw new BehaviorException(ioe);
        }
    }

    /**
     * {@inheritDoc}
     */
    public abstract List<Runnable> getOperations(ClientSession session,
        Object[] args) throws BehaviorException;
}
