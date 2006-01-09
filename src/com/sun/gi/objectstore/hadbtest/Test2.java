/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;

/**
 * @author Daniel Ellard
 */

public class Test2 {

    static public void main(String[] args) {
	ObjectStore os = TestUtil.connect(true);

	if (TestUtil.sanityCheck(os, "Hello, World", true)) {
	    System.out.println("appears to work");
	}
	else {
	    System.out.println("yuck");
	    return;
	}

	// warm up the system.
	System.out.println("Warming up the system.");
	for (int i = 0; i < 1000; i++) {
	    TestUtil.sanityCheck(os, "Hello, World", false);
	}
	System.out.println("Warmed...");


	for (int i = 0; i < 4; i++) {
	    TestClientSharedConn t = new TestClientSharedConn(i, os);
	    Thread thread = new Thread(t);
	    thread.start();
	}

	os.close();
    }
}

class TestClientOwnConn implements Runnable {
    private ObjectStore os;
    private final long label;
    private long lastWake;
    private long mySleep = 40;
    private long iters = 10;
    private long[] oids;

    public TestClientOwnConn(long label) {
	this.label = label;
	oids = new long[(int)iters];
    }

    public void run() {
	System.out.println("starting up " + label + ": whoopee");
	os = TestUtil.connect(false);
	System.out.println("connected to ObjectStore\n");
	lastWake = System.currentTimeMillis();

	try {
	    for (int i = 0; i < 20; i++) {
		TestUtil.sanityCheck(os, "Hello, World", false);

		Thread.sleep(mySleep);
		long currTime = System.currentTimeMillis();
		long diff = currTime - lastWake;
		long drift = diff - mySleep;
		System.out.println("awake " + label + " at " + diff +
			" vs. " + mySleep + " drift " + drift);
		lastWake = currTime;
	    }
	}
	catch (Exception e) {
	    System.out.println("bye " + label);
	    return;
	}
	return;
    }
}

class TestClientSharedConn implements Runnable {
    private ObjectStore os;
    private final long label;
    private long lastWake;
    private long mySleep = 50;
    private long iters = 5;
    private long[] oids;

    public TestClientSharedConn(long label, ObjectStore os) {
	this.os = os;
	this.label = label;
	oids = new long[(int)iters];
    }

    public void run() {
	System.out.println("starting up " + label + ": whoopee");
	lastWake = System.currentTimeMillis();

	try {
	    for (int i = 0; i < 20; i++) {
		TestUtil.sanityCheck(os, "Hello, World", false);

		Thread.sleep(mySleep);
		long currTime = System.currentTimeMillis();
		long diff = currTime - lastWake;
		long drift = diff - mySleep;
		System.out.println("awake " + label + " at " + diff +
			" vs. " + mySleep + " drift " + drift);
		lastWake = currTime;
	    }
	}
	catch (Exception e) {
	    System.out.println("bye " + label);
	    return;
	}
	return;
    }
}


