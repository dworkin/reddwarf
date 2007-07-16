/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * TODO
 */
public class DatastoreCreateModule extends AbstractModuleImpl implements Serializable {

    private static final long serialVersionUID = 0x1234FCL;
    
    /** Empty constructor */
    public DatastoreCreateModule() { }
    
    // implement AbstractModuleImpl
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        String boundName = null, className = null;
        Class<?>[] ctorTypes = null;
        Object[] ctorArgs = null;
        
        initVars(new Object[] { boundName, className, ctorTypes, ctorArgs },
            new Class<?>[] { String.class, String.class, Class[].class,
                                 Object[].class }, args, 2);
        
        Class<?> clazz;
        try {
	    clazz = Class.forName(className);
	}
        catch (ClassNotFoundException cnfe) {
            throw new BehaviorException(cnfe);
        }
        Object obj;
        if (clazz.isArray()) {
            obj = Array.newInstance(clazz.getComponentType(), new int[] {
                (Integer)ctorArgs[0] });
        } else {
            /** Not an array... */
            try {
                Constructor ctor = clazz.getConstructor(ctorTypes);
                obj = ctor.newInstance(ctorArgs);
            } catch (IllegalAccessException iae) {
                throw new BehaviorException(iae);
            } catch (InvocationTargetException ite) {
                throw new BehaviorException(ite);
            } catch (NoSuchMethodException nsme) {
                throw new BehaviorException("invalid parameter(s) to " +
                    this + ": constructor not found.", nsme);
            } catch (InstantiationException ie) {
                throw new BehaviorException(ie);
            }
        }
        return createOperations(session, boundName, obj);
    }
    
    /*
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        String boundName = (String)in.readObject();
        String className = (String)in.readObject();
        Class<?> clazz = Class.forName(className);
        Object obj;
        
        if (clazz.isArray()) {
            obj = Array.newInstance(clazz.getComponentType(), new int[] {
                in.readInt() });
        } else {
            /** Not an array... */
            try {
                Class<?>[] ctorTypes = (Class<?>[])in.readObject();
                Object[] ctorArgs = (Object[])in.readObject();
                
                Constructor ctor = clazz.getConstructor(ctorTypes);
                obj = ctor.newInstance(ctorArgs);
            } catch (IllegalAccessException iae) {
                throw new BehaviorException(iae);
            } catch (InstantiationException ie) {
                throw new BehaviorException(ie);
            } catch (InvocationTargetException ite) {
                throw new BehaviorException(ite);
            } catch (NoSuchMethodException nsme) {
                throw new BehaviorException(nsme);
            }
        }
        
        return createOperations(session, boundName, obj);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final ClientSession session,
        final String name, final Object obj)
    {
        List<Runnable> operations = new LinkedList<Runnable>();
        
	operations.add(new Runnable() {
		public void run() {
                    DataManager dm = AppContext.getDataManager();
		    try {
                        if (obj instanceof ManagedObject)
                            dm.setBinding(name, (ManagedObject)obj);
                        else
                            dm.setBinding(name, new ManagedObjectWrapper(obj));
                        
                        System.out.println("Created DataStore binding for " +
                            " object of type " + obj.getClass().getName() +
                            " under name " + name + ".");
                    } catch (NotSerializableException nse) {
                        System.err.println("**Error: Cannot add object " +
                            " name to datastore; not serializable.");
                    }
                }
	    });
	return operations;
    }
}
