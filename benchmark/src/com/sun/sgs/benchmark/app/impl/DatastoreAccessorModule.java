package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;

import com.sun.sgs.benchmark.app.BehaviorModule;

/**
 * A loadable module that performs an access of the datastore.
 */
public class DatastoreAccessorModule implements BehaviorModule, Serializable {
    
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
	operations.add(new Runnable() {
		public void run() {
		    //AppContext.getDataManager().
		}
	    });
	return operations;
    }



}