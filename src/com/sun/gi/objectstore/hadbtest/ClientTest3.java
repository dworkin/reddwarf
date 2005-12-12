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
    private long iters = 5;
    private long[][] oidClusters;
    private int numPeeks = 1;
    private int numLocks = 1;
    private int numPromotedPeeks = 1;
    private Random r = new Random();

    public ClientTest3(long clientId, DerbyObjectStore os, long[][] oidClusters) {
	this.os = os;
	this.clientId = clientId;
	this.oidClusters = oidClusters;
    }

    public void run() {
	System.out.println("starting up " + clientId + ": whoopee");
	lastWake = System.currentTimeMillis();

	for (int i = 0; i < iters; i++) {
	    doRandomTransaction();
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

    private void doRandomTransaction() {
	int cluster = r.nextInt(oidClusters.length);

	long[][] participants = FakeAppUtil.pickRandomParticipants(
	    	oidClusters[cluster], numPeeks, numLocks, numPromotedPeeks);

	long[] peekOids = participants[0];
	long[] lockOids = participants[1];
	long[] promotedPeekOids = participants[2];

	Transaction trans = (Transaction) new DerbyObjectStoreTransaction(os);

	for (int i = 0; i < peekOids.length; i++) {
	    trans.peek(peekOids[i]); 
	}
	for (int i = 0; i < promotedPeekOids.length; i++) {
	    trans.peek(promotedPeekOids[i]); 
	}
	for (int i = 0; i < lockOids.length; i++) {
	    trans.lock(lockOids[i]); 
	}
	for (int i = 0; i < promotedPeekOids.length; i++) {
	    trans.lock(promotedPeekOids[i]); 
	}
    }
}
