/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

/**
 * @author Daniel Ellard
 */

public class ThreadCount {

    public static void main(String[] args) {

	for (int i = 0; i < 100; i++) {
	    String name = "worker(" + i + ")";
	    TestWorker worker = new TestWorker(name, 1000 + i);
	    Thread thread = new Thread(worker);

	    if (thread == null) {
		System.out.println("failed at thread " + i);
		return;
	    }

	    thread.start();
	}
    }
}

