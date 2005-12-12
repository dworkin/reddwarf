/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

/**
 * @author Daniel Ellard
 */

public class LaunchWorkers {

    public static void main(String[] args) {
	int WorkerCount = 4;	// &&& Make this a parameter

	for (int i = 0; i < WorkerCount; i++) {
	    String name = "worker(" + i + ")";
	    TestWorker worker = new TestWorker(name, 1 + i);
	    Thread thread = new Thread(worker);

	    thread.start();
	}
    }
}

