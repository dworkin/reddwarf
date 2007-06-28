package com.sun.sgs.benchmark.application;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.Task;

/**
 * A loadable module that performs an access of the datastore.
 *
 * @author Ian Rose
 * @author David Jurgens
 */
public class DatastoreAccessorModule implements BehaviorModule {

    /**
     * ?
     *
     * @param args op-codes denoting the arguments
     *
     * @return ?
     */
    public List<Task> getTasks(byte[] args) {
	LinkedList<Task> tasks = new LinkedList<Task>();
	return tasks;
    }

    /**
     * ?
     *
     * @param args op-codes denoting the arguments
     *
     * @return ?
     */
    public List<Task> getTasks(Object[] args) {
	LinkedList<Task> tasks = new LinkedList<Task>();
	return tasks;
    }

}