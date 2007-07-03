package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;

import java.lang.reflect.Constructor;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;

import com.sun.sgs.benchmark.app.BehaviorModule;

/**
 *
 */
public class MallocModule implements BehaviorModule, Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;

   /**
     * Returns an empty list of {@code Runnable} operations.
     *
     * @param args op-codes denoting the arguments
     *
     * @return an empty list
     */
    public List<Runnable> getOperations(ClientSession session, byte[] args) {
	return new LinkedList<Runnable>();
    }

    /**
     * Fill me in.
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args) {
	List<Runnable> operations = new LinkedList<Runnable>();
	if (args.length != 2) {
	    System.out.printf("invalid parameter(s) to %s: %s\n",
			      this, Arrays.toString(args));
	    return operations;
	}
	Class<?> type = null;
	Integer count = null;
	try {
	    type = (Class<?>)(args[0]);
	    count = (Integer)(args[1]);
	}
	catch (ClassCastException cce) {
	    System.out.printf("invalid parameter(s) to %s: %s\n" +
			      "expected Object type and count\n" ,
			      this, args[0]);
	}
	final Class<?> clazz = type;
	final int num = count.intValue();

	operations.add(new Runnable() {
		public void run() {
		    // keep a pointer so they all stay in memory at once
		    Object[] arr = new Object[num];
		    for (int i = 0; i < num; i++) {
			try {
			    Constructor<?> c = 
				clazz.getConstructor(new Class<?>[]{});
			    arr[i] = c.newInstance(new Object[]{});
			}
			catch (Throwable t) {
			    // silent
			    arr[i] = new Object(); // to fill space
			}
		    }
		}
	    });
	return operations;
    }

}