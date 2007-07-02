package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.Task;

import com.sun.sgs.benchmark.app.BehaviorModule;

/**
 * A loadable module that always generates an empty list of {@code
 * Runnable} operations.
 */
public class NoOpModule implements BehaviorModule, Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;

   /**
     * Returns an empty list of {@code Runnable} operations.
     *
     * @param args op-codes denoting the arguments
     *
     * @return an empty list
     */
    public List<Runnable> getOperations(byte[] args) {
	return new LinkedList<Runnable>();
    }

    /**
     * Returns an empty list of {@code Runnable} operations.
     *
     * @param args op-codes denoting the arguments
     *
     * @return an empty list
     */
    public List<Runnable> getOperations(Object[] args) {
	return new LinkedList<Runnable>();
    }

}