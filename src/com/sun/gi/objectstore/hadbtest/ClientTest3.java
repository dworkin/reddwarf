/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.TSOObjectStore;
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.PersistantInMemoryDataSpace;
import java.io.Serializable;
import java.util.Random;

/**
 * @author Daniel Ellard
 */

class ClientTest3 implements Runnable {
    private ObjectStore os;
    private final long clientId;
    private long lastWake;
    private long mySleep = 50;
    private long iters = 5000;
    private long[][] oidClusters;
    private int numPeeks = 4;
    private int numLocks = 2;
    private int numPromotedPeeks = 1;
    private Random r = new Random();
    private boolean verbose = false;
    private long sleepTime;

    public ClientTest3(long clientId, ObjectStore os, long[][] oidClusters,
	    long sleepTime) {
	this.os = os;
	this.clientId = clientId;
	this.oidClusters = oidClusters;
	this.sleepTime = sleepTime;
    }

    public void run() {
	System.out.println("starting up " + clientId + ": whoopee");
	lastWake = System.currentTimeMillis();

	long start = System.currentTimeMillis();
	for (int i = 0; i < iters; i++) {

	    if (i % 100 == 0) {
		System.out.println("snoozing at count " + i);
		try {
		    Thread.sleep(sleepTime);
		} catch (Exception e) {
		}

		System.out.println("snoozing at count " + i);
	    }

	    if (i % 50 == 0) {
		System.out.println("at count " + i);
	    }

	    if ((i > 0) && ((i % 1000) == 0)) {
		long now = System.currentTimeMillis();
		long elapsed = start - now;
		start = now;
		System.out.println("elapsed for 1000: " + elapsed);
	    }
	    try {
		doRandomTransaction(verbose);
	    } catch (Exception e) {
		// DJE:
		System.out.println("unexpected exception: " + e);
		e.printStackTrace(System.out);
	    }
	}

	return;
    }

    public void setNumPeeks(int numPeeks) {
	this.numPeeks = numPeeks;
    }
    public void setNumLocks(int numLocks) {
	this.numLocks = numLocks;
    }
    public void setNumPromotedPeeks(int numPromotedPeeks) {
	this.numPromotedPeeks = numPromotedPeeks;
    }

    private void doRandomTransaction(boolean verbose)
	    throws NonExistantObjectIDException
    {
	int cluster = r.nextInt(oidClusters.length);

	long[][] participants = FakeAppUtil.pickRandomParticipants(
	    	oidClusters[cluster], numPeeks, numLocks, numPromotedPeeks);

	long[] peekOids = participants[0];
	long[] lockOids = participants[1];
	long[] promotedPeekOids = participants[2];

	Transaction trans = os.newTransaction(null);
	trans.start();

	if (verbose) { System.out.println("peeks: "); }

	for (int i = 0; i < peekOids.length; i++) {
	    if (verbose) { System.out.print(peekOids[i] + " "); }
	    trans.peek(peekOids[i]); 
	}
	if (verbose) { System.out.println(); }

	if (verbose) { System.out.println("peekPromoted: "); }
	for (int i = 0; i < promotedPeekOids.length; i++) {
	    if (verbose) { System.out.print(promotedPeekOids[i] + " "); }
	    trans.peek(promotedPeekOids[i]); 
	}
	if (verbose) { System.out.println(); }

	if (verbose) { System.out.println("locks: "); }
	for (int i = 0; i < lockOids.length; i++) {
	    if (verbose) { System.out.print(lockOids[i] + " "); }
	    trans.lock(lockOids[i]); 
	}
	if (verbose) { System.out.println(); }

	if (verbose) { System.out.println("promotedToLock: "); }
	for (int i = 0; i < promotedPeekOids.length; i++) {
	    if (verbose) { System.out.print(promotedPeekOids[i] + " "); }
	    trans.lock(promotedPeekOids[i]); 
	}
	if (verbose) { System.out.println(); }

	trans.commit();
    }
}
