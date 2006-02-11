/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;

/**
 * @author Daniel Ellard
 */

public class Test4 {
    static public void main(String[] args) {

	/*
	 * The params should depend on the args.  At present
	 * everything is hard-wired.
	 */

	TestParams params = new TestParams();

	/*
	 * Connect to the database, test the connection.
	 */

	// ObjectStore os = TestUtil.connect(false);
	ObjectStore os = TestUtil.connect(1, false, "hadb", null);
	// ObjectStore os = TestUtil.connect(1, true, "hadb", "Hlog1");

	if (TestUtil.sanityCheck(os, "Hello, World", true)) {
	    System.out.println("appears to work");
	}
	else {
	    System.out.println("yuck");
	    return;
	}

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

	ObjectCreator creator = new ObjectCreator(os, 0, null, params.objSize);
	long[] oids = creator.createNewBunch(params.numObjs, 1, false);

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

	System.out.println("waiting for the new objects to settle");
	try {
	    Thread.sleep(10000);
	} catch (Exception e) {
	}

	System.out.println("continuing...");

	for (long snooze = 0; snooze >= 0; snooze -= 1) {
	    ClientTest4 t = new ClientTest4(10, os, clusters, snooze, 1000);

	    t.run();

	    /*
	    Thread thread = new Thread(t);
	    thread.start();

	    try {
		thread.join();
	    } catch (Exception e) {
		System.out.println("unexpected: " + e);
	    }
	    */

	    // Let everything settle down...
	    try {
		System.out.println("starting snooze: " + snooze);
		Thread.sleep(5000);
	    } catch (Exception e) {
		System.out.println("unexpected: " + e);
	    }

	}

	os.close();
    }
}

