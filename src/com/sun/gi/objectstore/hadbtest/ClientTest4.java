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

class ClientTest4 implements Runnable {
    private ObjectStore os;
    private final long clientId;
    private long lastWake;
    private long[][] oidClusters;
    private int numPeeks = 4;
    private int numLocks = 2;
    private int numPromotedPeeks = 1;
    private Random r = new Random();
    private boolean verbose = false;
    private long iters;
    private long sleepTime;
    Transaction trans;

    public ClientTest4(long clientId, ObjectStore os, long[][] oidClusters,
	    long sleepTime, long iters) {
	this.os = os;
	this.clientId = clientId;
	this.oidClusters = oidClusters;
	this.sleepTime = sleepTime;
	this.iters = iters;
	this.trans = os.newTransaction(null);
    }

    public void run() {
	System.out.println("starting up " + clientId + ": whoopee");
	lastWake = System.currentTimeMillis();

	int incr = 100;
	for (int count = 0; count < iters; count += incr) {
	    long start = System.currentTimeMillis();
	    for (int i = 0; i < incr; i++) {
		try {
		    doRandomTransaction(verbose);
		    if (sleepTime > 0) {
			Thread.sleep(sleepTime);
		    }
		} catch (Exception e) {
		    // DJE:
		    System.out.println("unexpected exception: " + e);
		    e.printStackTrace(System.out);
		}
	    }
	    long now = System.currentTimeMillis();
	    long elapsed = now - start;
	    double rate = elapsed / (double) incr;
	    System.out.println("elapsed for " + sleepTime + " ms " +
		    incr + ": " + elapsed + "  " + rate + "/ms");
	    System.out.println("\tat count " + count);
	    try {
		Thread.sleep(5000);
	    } catch (Exception e) {
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
	    FillerObject fo = (FillerObject) trans.peek(peekOids[i]); 
	    if (fo.getOID() != peekOids[i]) {
		System.out.println("CHECK FAILED: " + fo.getOID() + " != " +
			peekOids[i]);
	    }
	}
	if (verbose) { System.out.println(); }

	if (verbose) { System.out.println("peekPromoted: "); }
	for (int i = 0; i < promotedPeekOids.length; i++) {
	    if (verbose) { System.out.print(promotedPeekOids[i] + " "); }
	    FillerObject fo = (FillerObject) trans.peek(promotedPeekOids[i]); 
	    if (fo.getOID() != promotedPeekOids[i]) {
		System.out.println("CHECK FAILED: " + fo.getOID() + " != " +
			promotedPeekOids[i]);
	    }
	}
	if (verbose) { System.out.println(); }

	if (verbose) { System.out.println("locks: "); }
	for (int i = 0; i < lockOids.length; i++) {
	    if (verbose) { System.out.print(lockOids[i] + " "); }
	    FillerObject fo = (FillerObject) trans.lock(lockOids[i]); 
	    if (fo.getOID() != lockOids[i]) {
		System.out.println("CHECK FAILED: " + fo.getOID() + " != " +
			lockOids[i]);
	    }
	}
	if (verbose) { System.out.println(); }

	if (verbose) { System.out.println("promotedToLock: "); }
	for (int i = 0; i < promotedPeekOids.length; i++) {
	    if (verbose) { System.out.print(promotedPeekOids[i] + " "); }
	    FillerObject fo = (FillerObject) trans.lock(promotedPeekOids[i]); 
	    if (fo.getOID() != promotedPeekOids[i]) {
		System.out.println("CHECK FAILED: " + fo.getOID() + " != " +
			promotedPeekOids[i]);
	    }
	}
	if (verbose) { System.out.println(); }

	trans.commit();
    }
}
