/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.impl.DerbyObjectStore;

/**
 * @author Daniel Ellard
 */

public class Test3 {
    static public void main(String[] args) {

	/*
	 * The params should depend on the args.  At present
	 * everything is hard-wired.
	 */

	TestParams params = new TestParams();

	/*
	 * Connect to the database, test the connection.
	 */

	DerbyObjectStore os = TestUtil.connect(true);

	if (TestUtil.sanityCheck(os, "Hello, World", true)) {
	    System.out.println("appears to work");
	}
	else {
	    System.out.println("yuck");
	    return;
	}

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

	for (int i = 0; i < params.numThreads; i++) {
	    ClientTest3 t = new ClientTest3(i, os, clusters);
	    Thread thread = new Thread(t);
	    thread.start();
	}
    }
}

