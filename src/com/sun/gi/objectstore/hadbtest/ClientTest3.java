/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.impl.DerbyObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.impl.DerbyObjectStoreTransaction;
import java.util.Random;

/**
 * @author Daniel Ellard
 */

class ClientTest3 implements Runnable {
    private DerbyObjectStore os;
    private final long clientId;
    private long lastWake;
    private long mySleep = 50;
    private long iters = 100;
    private long[][] oidClusters;
    private int numPeeks = 4;
    private int numLocks = 2;
    private int numPromotedPeeks = 1;
    private Random r = new Random();
    private boolean verbose = false;

    public ClientTest3(long clientId, DerbyObjectStore os, long[][] oidClusters) {
	this.os = os;
	this.clientId = clientId;
	this.oidClusters = oidClusters;
    }

    public void run() {
	System.out.println("starting up " + clientId + ": whoopee");
	lastWake = System.currentTimeMillis();

	for (int i = 0; i < iters; i++) {
	    doRandomTransaction(verbose);
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

    private void doRandomTransaction(boolean verbose) {
	int cluster = r.nextInt(oidClusters.length);

	long[][] participants = FakeAppUtil.pickRandomParticipants(
	    	oidClusters[cluster], numPeeks, numLocks, numPromotedPeeks);

	long[] peekOids = participants[0];
	long[] lockOids = participants[1];
	long[] promotedPeekOids = participants[2];

	Transaction trans = (Transaction) new DerbyObjectStoreTransaction(os);

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
    }
}
