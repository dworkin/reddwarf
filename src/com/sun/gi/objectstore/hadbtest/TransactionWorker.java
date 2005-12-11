/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.load;

/**
 * @author Daniel Ellard
 */

public class TransactionWorker implements Runnable {
    private final String name;
    private final long interEventPause;
    private final long myObjCount;
    private long lastWake;
    private final long nEvents;

    public TransactionWorker(String name, long sleep, long nEvents,
	    int objCount) {
	this.name = name;
	this.interEventPause = sleep;
	this.nEvents = nEvents;
	this.myObjCount = objCount;
    }

    public void run() {

	System.out.println("starting up worker: " + name);
	lastWake = System.currentTimeMillis();

	String log = "";
	try {
	    for (int i = 0; i < nEvents; i++) {
		long currTime = System.currentTimeMillis();
		long diff = currTime - lastWake;
		long drift = diff - interEventPause;
		log += "awake " + name + "at " + diff +
			" vs. " + interEventPause + " drift " + drift + "\n";
		lastWake = currTime;
		Thread.sleep(interEventPause);
	    }
	}
	catch (Exception e) {
	    System.out.println("Exception in " + name + ".  Bye.");
	    return;
	}
	System.out.println(log);
	return;
    }
}

