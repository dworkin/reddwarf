/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

/**
 * @author Daniel Ellard
 */

public class Test5 {

    static public void main(String[] args) {
	long appID = 1;

	/*
	 * The params should depend on the args.  At present
	 * everything is hard-wired.
	 */

	if ((args.length != 0) && (args.length != 1)) {
	    System.out.println("incorrect usage");
	    System.exit(1);
	}

	TestParams params;
	if (args.length == 0) {
	    params = new TestParams();
	} else {
	    params = new TestParams(args[1]);
	}
	System.out.println(params.toString());

	/*
	 * Connect to the database, test the connection.
	 */

	ObjectStore os = TestUtil.connect(appID, false,
		params.dataSpaceType, params.traceFileName);

	if (TestUtil.sanityCheck(os, "Hello, World", true)) {
	    System.out.println("appears to work");
	}
	else {
	    System.out.println("yuck");
	    return;
	}

	os.clear();

	/*
	 * Create a bunch of objects, and then chop them up into clusters.
	 */

	ObjectCreator creator = new ObjectCreator(os, 0, null);
	long[] oids = creator.createNewBunch(params.numObjs, params.objSize,
		1, true);

	if (params.dataSpaceType.equals("persistant-inmem")) {
	    TestUtil.snooze(10000, "waiting for the new objects to settle");
	}

	os.close();

	lookupTest(appID, oids, params, 1, 2);
	lookupTest(appID, oids, params, 100, 2);

	accessTest(appID, oids, params, true, 1, 2);
	accessTest(appID, oids, params, true, 100, 2);
	accessTest(appID, oids, params, false, 1, 2);
	accessTest(appID, oids, params, false, 100, 2);

	// transactionTest(appID, oids, params);
    }

    private static void transactionTest(long appID, long[] oids,
	    TestParams params) {

	long[][] clusters;
	try {
	    clusters = FakeAppUtil.createRelatedClusters(oids,
		    params.numObjs / params.clusterSize, params.clusterSize,
		    params.skipSize);
	}
	catch (Exception e) {
	    e.printStackTrace(System.out);
	    return ;
	}

	System.out.println(params.toString());

	//System.out.println("transactionTest numObjs " + params.numObjs);
	//System.out.println("transactionTest clusterSize " + params.clusterSize);
	//System.out.println("transactionTest skipSize " + params.skipSize);

	for (long snooze = 0; snooze >= 0; snooze -= 1) {

	    if (params.numThreads == 1) {
		System.out.println("starting one thread");
		ClientTest5 t = new ClientTest5(appID, 0, params,
			clusters, snooze, 1000);
		t.run();
	    } else {
		System.out.println("starting " + params.numThreads +
			" threads");

		ClientTest5[] clients = new ClientTest5[params.numThreads];
		Thread[] allThreads = new Thread[params.numThreads];
		
		for (int i = 0; i < params.numThreads; i++) {
		    clients[i] = new ClientTest5(appID, i, params,
			    clusters, snooze, 1000);
		    allThreads[i] = new Thread(clients[i]);
		    allThreads[i].start();
		}

		for (int i = 0; i < params.numThreads; i++) {
		    try {
			allThreads[i].join();
		    } catch (Exception e) {
			System.out.println("unexpected: " + e);
		    }
		}
	    }
	    TestUtil.snooze(5000, "waiting for activity to drain");
	}
	System.out.println("done");
    }

    private static void accessTest(long appID, long[] oids, TestParams params,
	    boolean doLock, int opsPerTrans, int laps) {
	long startTime, endTime;
	Transaction trans = null;

	ObjectStore os = TestUtil.connect(appID, false,
		params.dataSpaceType, null);

	startTime = System.currentTimeMillis();

	int opsSeenInTrans = 0;
	trans = os.newTransaction(null);
	trans.start();

	for (int lap = 0; lap < laps; lap++) {
	    for (int i = 0; i < oids.length; i++) {

		try {
		    FillerObject fo;

		    if (doLock) {
			fo = (FillerObject) trans.lock(oids[i]); 
		    } else {
			fo = (FillerObject) trans.peek(oids[i]); 
		    }
		    if (fo.getOID() != oids[i]) {
			System.out.println("CHECK FAILED: " + fo.getOID() + " != " +
				oids[i]);
		    }
		} catch (Exception e) {
		    System.out.println("iter: " + i + " oid: " + oids[i]);
		    System.out.println("unexpected: " + e);
		    e.printStackTrace(System.out);
		    System.exit(1);
		}

		if (opsSeenInTrans++ == opsPerTrans) {
		    trans.commit();
		    opsSeenInTrans = 0;
		    trans = os.newTransaction(null);
		    trans.start();
		}

	    }
	}

	endTime = System.currentTimeMillis();

	long totalVisited = oids.length * laps;

	long elapsed = endTime - startTime;
	double ave = elapsed / (double) totalVisited;

	System.out.println("EE: elapsed " + elapsed +
		" totalOps " + totalVisited);

	System.out.println("ave " +
	    	    (doLock ? "LOCK: " : "PEEK: ") + ave + " ms" + 
		    " trans size " + opsPerTrans);

	System.out.println("draining...");
	os.close();
	System.out.println("drained.");
    }

    private static void lookupTest(long appID, long[] oids, TestParams params,
	    int opsPerTrans, int laps) {
	long startTime, endTime;
	Transaction trans = null;

	/* 
	 * The assumption here is that the oids were created by
	 * createNewBunch, starting with a blank slate, and therefore
	 * we know exactly what names each object has.  This is a big
	 * assumption...
	 */

	ObjectStore os = TestUtil.connect(appID, false,
		params.dataSpaceType, null);

	startTime = System.currentTimeMillis();

	int opsSeenInTrans = 0;
	trans = os.newTransaction(null);
	trans.start();

	for (int lap = 0; lap < laps; lap++) {
	    for (int i = 0; i < oids.length; i++) {

		String name = ObjectCreator.idString(appID, i);

		try {
		    long oid = trans.lookup(name); 
		    if (oid != oids[i]) {
			System.out.println("CHECK FAILED: " + oid + " != " +
				oids[i]);
		    }
		} catch (Exception e) {
		    System.out.println("iter: " + i + " oid: " + oids[i]);
		    System.out.println("unexpected: " + e);
		    e.printStackTrace(System.out);
		    System.exit(1);
		}

		if (opsSeenInTrans++ == opsPerTrans) {
		    trans.commit();
		    opsSeenInTrans = 0;
		    trans = os.newTransaction(null);
		    trans.start();
		}

	    }
	}

	if (opsSeenInTrans > 0) {
	    trans.commit();
	}
	endTime = System.currentTimeMillis();

	long totalVisited = oids.length * laps;
	long elapsed = endTime - startTime;
	double ave = elapsed / (double) totalVisited;

	System.out.println("EE: elapsed " + elapsed +
		" totalOps " + totalVisited);

	System.out.println("ave lookup speed: " + ave + " ms" +
		" trans size " + opsPerTrans);
	os.close();
    }
}

