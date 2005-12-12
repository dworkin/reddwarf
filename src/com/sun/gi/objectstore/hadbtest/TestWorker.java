/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

/**
 * @author Daniel Ellard
 */

public class TestWorker implements Runnable {
    private final String myName;
    private final long mySleep;
    private long lastWake;

    public TestWorker(String name, long sleep) {
	this.myName = name;
	this.mySleep = sleep;
    }

    public void run() {

	System.out.println("starting up " + myName + ": whoopee");
	lastWake = System.currentTimeMillis();

	String log = "";
	try {
	    for (int i = 0; i < 10; i++) {
		Thread.sleep(mySleep);
		long currTime = System.currentTimeMillis();
		long diff = currTime - lastWake;
		long drift = diff - mySleep;
		log += "awake " + myName + "at " + diff +
			" vs. " + mySleep + " drift " + drift + "\n";
		lastWake = currTime;
	    }
	}
	catch (Exception e) {
	    System.out.println("bye" + myName);
	    return;
	}
	System.out.println(log);
	return;
    }
}

