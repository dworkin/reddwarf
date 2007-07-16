package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.benchmark.app.BehaviorModule;

/**
 * A loadable module that performs an access of the datastore.
 */
public class DatastoreCreateModule implements BehaviorModule, Serializable {
    
    private static final long serialVersionUID = 0x1234FCL;

    /**
     * ?
     *
     * @param args op-codes denoting the arguments
     *
     * @return ?
     */
    public List<Runnable> getOperations(ClientSession session, byte[] args) {
	LinkedList<Runnable> operations = new LinkedList<Runnable>();
	return operations;
    }

    /**
     * ?
     *
     * @param args op-codes denoting the arguments
     *
     * @return ?
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args) {
	LinkedList<Runnable> operations = new LinkedList<Runnable>();
	if (args.length <2 || args.length > 4) {
	    System.out.printf("invalid number of args for datastore "
			      + "create: %d\n", args.length);
	    return operations;
	}

	String boundName = "";
	String className = "";
	Class<?>[] argTypes = null;
	Object[] objArgs = null;
	int arrayLength = -1;
	try {
	    boundName = (String)args[0];
	    className = (String)args[1];
	    if (args.length == 2) {
		argTypes = new Class<?>[] { };
		objArgs = new Object[] { };
	    }
	    else if (args.length == 3) {
		arrayLength = ((Integer)args[2]).intValue();
	    }
	    else {
		argTypes = (Class<?>[])(args[2]);
		objArgs = (Object[])(args[3]);
	    }
	}
	catch (ClassCastException cce) {
	    System.out.println("invalid arg types for datastore create");
	    return operations;
	}


	// make the final references
	final String boundName_ = boundName;
	final String className_ = className;
	final Class<?>[] argTypes_ = argTypes;
	final Object[] objArgs_ = objArgs;
	final int arrayLength_ = arrayLength;
		
	operations.add(new Runnable() {
		public void run() {
		    
		    // try to reflectively instantiate the object
		    try {
			Class<?> clazz = Class.forName(className_);
			ManagedObject m = (arrayLength_ > 0)
			    ? new ArrayWrapper(Array.newInstance(clazz,
								 arrayLength_))
			    : (ManagedObject)
			    (clazz.getConstructor(argTypes_).
			     newInstance(objArgs_));
			
			AppContext.getDataManager().setBinding(boundName_, m); 
		    }
		    catch (Exception e) {
			e.printStackTrace();
		    }
		}
	    });
	return operations;
    }

    private static class ArrayWrapper implements Serializable, ManagedObject {

	private final Object array;

	public ArrayWrapper(Object array) {
	    this.array = array;
	}

    }

}