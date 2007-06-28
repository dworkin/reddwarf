package com.sun.sgs.benchmark.application;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.Task;

/**
 * A loadable module that always generates an empty list of {@code
 * Task} objects.
 *
 * @author Ian Rose
 * @author David Jurgens
 */
public class NoOpModule implements BehaviorModule {

    /**
     * Returns an empty list of {@code Task} objects.
     *
     * @param args op-codes denoting the arguments
     *
     * @return an empty list
     */
    public List<Task> getTasks(byte[] args) {
	return new LinkedList<Task>();
    }

    /**
     * Returns an empty list of {@code Task} objects.
     *
     * @param args op-codes denoting the arguments
     *
     * @return an empty list
     */
    public List<Task> getTasks(Object[] args) {
	return new LinkedList<Task>();
    }

}