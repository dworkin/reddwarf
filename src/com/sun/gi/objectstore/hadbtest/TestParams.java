/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;

/**
 * Parameters for the larger tests.  <p>
 *
 * This is only to simplify the tests.  Not be released into the wild. 
 * This is fairly awful software engineering, because it's just a
 * bunch of fields floating around where anyone can read/write them,
 * without any checks that the values make any sense at all. <p>
 *
 * @author Daniel Ellard
 */

public class TestParams {

    /**
     * The size of the typical objects in the database.
     */

    public int objSize = 5 * 1024;

    /**
     * The number of objects to stuff into the database.
     */

    public int numObjs = 10 * 1024;

    /**
     * The number of objects in a typical cluster.  A cluster is set
     * of objects that are usually involved in the same transactions. 
     * For example, they may be all of the objects owned by a
     * particular player.
     */

    public int clusterSize = 20;

    /**
     * The number of oids to skip between adjacent oids in the same
     * cluster.  A value of 0 just means to take adjacent oids.  A
     * value of ((numObjs / clusterSize) - 1) results in what is
     * anticipated to be the worst case on many systems.
     */

    public int skipSize = 0;

    /**
     * The number of concurrent threads to start.
     */

    public int numThreads = 4;

    public TestParams() { }

    public TestParams(int objSize, int numObjs, int clusterSize, int skipSize,
	    int numThreads) {
	this.objSize = objSize;
	this.numObjs = numObjs;
	this.clusterSize = clusterSize;
	this.skipSize = skipSize;
	this.numThreads = numThreads;
    }
}
