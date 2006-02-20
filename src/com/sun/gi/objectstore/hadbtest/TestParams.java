/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Enumeration;

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

    public int objSize;

    /**
     * The number of objects to stuff into the database.
     */

    public int numObjs;

    /**
     * The number of objects in a typical cluster.  A cluster is set
     * of objects that are usually involved in the same transactions. 
     * For example, they may be all of the objects owned by a
     * particular player.
     */

    public int clusterSize;

    /**
     * The number of oids to skip between adjacent oids in the same
     * cluster.  A value of 0 just means to take adjacent oids.  A
     * value of ((numObjs / clusterSize) - 1) results in what is
     * anticipated to be the worst case on many systems.
     */

    public int skipSize;

    /**
     * The number of concurrent threads to start.
     */

    public int numThreads;

    public int numIters;

    public int transactionNumPeeks;

    public int transactionNumLocks;

    public int transactionNumPromotedPeeks;

    /**
     * Whether or not to perform a trace.
     */
    public boolean doTrace = false;

    /**
     * Where to store the trace (if applicable).
     */
    public String traceFileName = null;

    /**
     * one of "persistant-inmem" (memory, backed by Derby),
     * "inmem" (memory-only), or "hadb" (for HADB).
     */

    public String dataSpaceType = "persistant-inmem";

    Properties params = new Properties();

    public TestParams() {
	initialize();
    }

    public TestParams(int objSize, int numObjs, int clusterSize, int skipSize,
	    int numThreads) {

	initialize();

	this.objSize = objSize;
	this.numObjs = numObjs;
	this.clusterSize = clusterSize;
	this.skipSize = skipSize;
	this.numThreads = numThreads;
    }

    public TestParams(String paramFileName) {
	this();

	setParamFile(paramFileName);
	initialize();
    }

    private void setParamFile(String paramFileName) {

	if (paramFileName == null) {
	    throw new NullPointerException("paramFileName is null");
	}

	FileInputStream fis = null;
	Properties params = new Properties();

	if (paramFileName != null) {
	    try {
		fis = new FileInputStream(paramFileName);
	    } catch (IOException e) {
		System.out.println("failed to open params file (" +
			paramFileName + "): " + e);
		throw new IllegalArgumentException("bad params file");
	    }

	    if (fis != null) {
		try {
		    params.load(fis);
		} catch (IOException e) {
		    System.out.println("failed to read params file (" +
			    paramFileName + "): " + e);
		    params.clear();
		    throw new IllegalArgumentException("bad params file");
		} catch (IllegalArgumentException e) {
		    System.out.println("params file (" +
			    paramFileName + ") contains errors: " + e);
		    params.clear();
		    throw new IllegalArgumentException("bad params file");
		}

		try {
		    fis.close();
		} catch (IOException e) {
		    // XXX: Is this really a problem?
		}
	    } 
	}
    }

    private void initialize() {

	dataSpaceType = (String) params.getProperty("hadbtest.dataSpaceType",
		System.getProperty("hadbtest.dataSpaceType",
			"persistant-inmem"));

	System.out.println("XXX: dataSpaceType=" + dataSpaceType);

	doTrace = (boolean) new Boolean(
	    	params.getProperty("hadbtest.doTrace",
			System.getProperty("hadbtest.doTrace",
				"false")));

	traceFileName = (String) params.getProperty("hadbtest.traceFileName",
		(String) System.getProperty("hadbtest.traceFileName",
			null));

	objSize = (int) new Integer(
		params.getProperty("hadbtest.objSize",
			System.getProperty("hadbtest.objSize",
				"10240")));

	numObjs = (int) new Integer(
	    	params.getProperty("hadbtest.numObjs",
			System.getProperty("hadbtest.numObjs",
				"1000")));

	clusterSize = (int) new Integer(
	    	params.getProperty("hadbtest.clusterSize",
			System.getProperty("hadbtest.clusterSize",
				"20")));
	skipSize = (int) new Integer(
	    	params.getProperty("hadbtest.skipSize",
			System.getProperty("hadbtest.skipSize",
				"4")));
	numThreads = (int) new Integer(
	    	params.getProperty("hadbtest.numThreads",
			System.getProperty("hadbtest.numThreads",
				"1")));
	numIters = (int) new Integer(
	    	params.getProperty("hadbtest.numIters",
			System.getProperty("hadbtest.numIters",
				"1000")));

	transactionNumPeeks = (int) new Integer(
	    	params.getProperty("hadbtest.transaction.numPeeks",
			System.getProperty("hadbtest.transaction.numPeeks",
				"4")));
	transactionNumLocks = (int) new Integer(
	    	params.getProperty("hadbtest.transaction.numLocks",
			System.getProperty("hadbtest.transaction.numLocks",
				"2")));
	transactionNumPromotedPeeks = (int) new Integer(
	    	params.getProperty("hadbtest.transaction.numPromotedPeeks",
			System.getProperty("hadbtest.transaction.numPromotedPeeks",
				"1")));

    }

    public String toString() {

	/* A StringBuffer would be faster..." */
	String s = "";
	s += "hadbtest.dataSpaceType: " + dataSpaceType + "\n";

	s += "hadbtest.doTrace: " + doTrace + "\n";

	if (doTrace) {
	    s += "hadtest.traceFileName: " + traceFileName + "\n";
	}

	s += "hadbtest.objSize: " + objSize + "\n";
	s += "hadbtest.numObjs: " + numObjs + "\n";
	s += "hadbtest.clusterSize: " + clusterSize + "\n";
	s += "hadbtest.skipSize: " + skipSize + "\n";
	s += "hadbtest.numThreads: " + numThreads + "\n";
	s += "hadbtest.numIters: " + numIters + "\n";
	s += "hadbtest.numThreads: " + numThreads + "\n";
	s += "hadbtest.transaction.numPeeks: " + transactionNumPeeks + "\n";
	s += "hadbtest.transaction.numLocks: " + transactionNumLocks + "\n";
	s += "hadbtest.transaction.numPromotedPeeks: " +
		transactionNumPromotedPeeks + "\n";

	return s;
    }

    public String properties() {

	Enumeration e = params.propertyNames();

	while (e.hasMoreElements()) {
	    String name = (String) e.nextElement();
	    System.out.println(name);
	}

	return "Write Me...";
    }
}
