/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.TransactionConflictException;

import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.benchmark.app.BehaviorModule;
import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * A loadable module that performs an access of the datastore.
 */
public class DatastoreAccessorModule extends AbstractModuleImpl implements Serializable {
    
    private static final long serialVersionUID = 0x1234FCL;
    
    /** Empty constructor */
    public DatastoreAccessorModule() { }
    
    // implement AbstractModuleImpl
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        String boundName = null;
        Boolean markForUpdate = new Boolean(false);
        
        initVars(new Object[] { boundName, markForUpdate },
            new Class<?>[] { String.class, Boolean.class }, args, 1);
        
	return createOperations(boundName, markForUpdate.booleanValue());
    }
    
    /**
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        String boundName = (String)in.readObject();
        boolean markForUpdate = (in.available() > 0) ? in.readBoolean() : false;
        
        return createOperations(boundName, markForUpdate);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final String name,
        final boolean markForUpdate)
    {
        List<Runnable> operations = new LinkedList<Runnable>();
        
	operations.add(new Runnable() {
		public void run() {
                    try {
                        ManagedObject o = AppContext.getDataManager().
                            getBinding(name, ManagedObject.class);
                        if (markForUpdate)
                            AppContext.getDataManager().markForUpdate(o);
                        
                        if (BehaviorModule.ENABLE_INFO_OUTPUT)
                            System.out.printf("%s: Accessed object \"%s\" " +
                                "from DataStore (for %s).\n", this, name,
                                (markForUpdate ? "writing" : "reading"));
                    } catch (NameNotBoundException nnbe) {
                        System.err.println("**Error: Client attempted to read" +
                            " object " + name + " which does not exist");
                    } catch (TransactionConflictException tce) {
                        /**
                         * Do nothing; we catch this simply because its not
                         * truly an error case (we expect this to happen
                         * normally during periods of high contention) and thus
                         * we don't want it propagating up and printing to
                         * stdout. 
                         */
                    }
		}
	    });
	return operations;
    }
}
