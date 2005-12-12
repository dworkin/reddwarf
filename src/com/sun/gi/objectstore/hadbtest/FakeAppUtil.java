/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

/**
 * @author Daniel Ellard
 */

public class FakeAppUtil {

    /**
     * @param oids an array of OIDs to partition into clusters.
     *
     * @param clusterCount the number of clusters to create.  If less
     * than zero, then create as many clusters as possible (of the
     * requested clusterSize).  If the number of oids is too small
     * to create the requested number of clusters, as many clusters
     * as possible are created.
     *
     * @param clusterSize the number of oids in each cluster.  If zero
     * or negative, throws IllegalArgumentException.
     *
     * @param skipSize the number of elements in the oid array to skip
     * between selections to prevent the oids in each cluster from
     * being adjacent in the database (which may happen if the oids
     * are created sequentially).  This number is used as hint; for
     * different values of the parameter it is not possible to obey
     * this value exactly.  For example, if you ask it to make a
     * cluster of 10 oids that are separated by 15 out of pool of 20
     * oids, the request cannot be satisfied but this method will
     * still create a cluster of 10 oids.  A value of 0 makes
     * partitions the oid array into adjacent pieces.
     */

    public static long[][] createRelatedClusters(long[] oids,
    	    int clusterCount, int clusterSize, int skipSize)
	    throws Exception {

	if (clusterCount < 1) {
	    throw new IllegalArgumentException("clusterCount < 1 ( " +
	    	    clusterCount + ")");
	}

	if (clusterCount < 0 || clusterCount > (oids.length / clusterSize)) {
	    clusterCount = oids.length / clusterSize;
	}

	long[][] oidClusters = new long[clusterCount][clusterSize];

	if (skipSize == 0) {
	    for (int i = 0; i < clusterCount; i++) {
		for (int j = 0; j < clusterSize; j++) {
		    oidClusters[i][j] = oids[i * clusterSize + j];
		}
	    }
	}
	else {

	    /*
	     * I know this is the stupid, inelegant way, but brute
	     * force is easier to debug, and this doesn't have to run
	     * quickly.
	     */

	    boolean[] oidsUsed = new boolean[oids.length];

	    // Probably unnecessary.
	    for (int i = 0; i < oidsUsed.length; i++) {
		oidsUsed[i] = false;
	    }

	    int currPos = 0;
	    int candidate, start, attempts;
	    int firstUnused = 0;

	    for (int i = 0; i < clusterCount; i++) {
		while (oidsUsed[firstUnused]) {
		    firstUnused++;
		}
		candidate = firstUnused;

		for (int j = 0; j < clusterSize; j++) {
		    start = candidate;
		    attempts = 0;

		    while (oidsUsed[candidate]) {
			candidate += (skipSize + 1);
			candidate %= oids.length;
			if (candidate == start) {
			    candidate++;
			    candidate %= oids.length;
			    start = candidate;
			}
			if (attempts++ == oids.length) {
			    // tried everything!
			    System.out.println("Tried everything; failed.");
			    throw new Exception("can't handle args");
			}
		    }

		    oidClusters[i][j] = oids[candidate];
		    oidsUsed[candidate] = true;
		}
	    }
	}

	return oidClusters;
    }

    /**
     * Choose the participant objects in a random transaction from to
     * given array of OIDs (<b>BROKEN</b>:  nothing random about it
     * right now).  <p>
     *
     * If the requested total number of participants is larger than
     * the number of OIDs, or the parameters are bogus in some other
     * way, then <code>null</code> is returned.  <p>
     *
     * @param oids the array of candiate OIDs.  Each OID is are
     * presumed to be unique; no duplicates.  This is not checked.
     *
     * @param peeks the number of objects that will be peeked.
     *
     * @param locks the number of objects that will be write-locked.
     *
     * @param promotedPeeks the number of objects that will be peeked
     * and then later locked.  (These objects are not counted in the
     * peeks or locks totals:  the total number of objects used in the
     * transaction is peeks + locks + promotedPeeks.)
     * 
     * @return an array of three arrays of longs.  The first array
     * contains the oids of the objects peeked, the second contains
     * the oids of objects locked, and the last contains the oids of
     * the objects that obtain peeks and later promote them.  (yeah, I
     * know this is fairly gross.)
     */

    public static long[][] pickRandomParticipants(long[] oids,
	    int peeks, int locks, int promotedPeeks) {

	if (oids == null || oids.length == 0) {
	    return null; // no oids to pick from.
	}

	if (peeks < 0 || locks < 0 || promotedPeeks < 0) {
	    return null; // just plain bogus.
	}

	if (peeks + locks + promotedPeeks < 1) {
	    return null; // requesting an empty participant set?  bogus.
	}

	if (oids.length < (peeks + locks + promotedPeeks)) {
	    return null; // an overly large participant set?  bogus.
	}

	long[][] results = new long[3][];
	results[0] = new long[peeks];
	results[1] = new long[locks];
	results[2] = new long[promotedPeeks];

	int pos = 0;
	for (int i = 0; i < peeks; i++) {
	    results[0][i] = oids[pos++];
	}
	for (int i = 0; i < locks; i++) {
	    results[1][i] = oids[pos++];
	}
	for (int i = 0; i < promotedPeeks; i++) {
	    results[2][i] = oids[pos++];
	}

	return results;
    }

    /**
     * Given an array of results returned by pickRandomParticipants,
     * coalesce them into a single array of the same form. <p>
     *
     * @see #pickRandomParticipants
     */

    public static long[][] coalesceParticipants(long[][][] oidArrays) {

	if (oidArrays == null) {
	    return null; // bogus.
	}
	int peeks = 0;
	int locks = 0;
	int promotedPeeks = 0;

	for (int i = 0; i < oidArrays.length; i++) {
	    peeks += oidArrays[i][0].length;
	    locks += oidArrays[i][1].length;
	    promotedPeeks += oidArrays[i][2].length;
	}

	long[][] results = new long[3][];
	results[0] = new long[peeks];
	results[1] = new long[locks];
	results[2] = new long[promotedPeeks];

	int pos[] = new int[3];

	for (int i = 0; i < oidArrays.length; i++) {
	    for (int type = 0; type < 3; type++) {
		for (int j = 0; j < oidArrays[i][0].length; j++) {
		    results[type][pos[type]++] = oidArrays[i][type][j];
		}
	    }
	}

	return results;
    }

    /**
     * Pretty-print a cluster array.  <p>
     *
     * Assumes that the cluster array is well-formed.  May do strange
     * things or crash otherwise. <p>
     */

    public static void printClusters(long[][] clusters) {
	for (int row = 0; row < clusters.length; row++) {
	    System.out.print("row " + row + ":");
	    for (int col = 0; col < clusters[0].length; col++) {
		System.out.print (" " + clusters[row][col]);
	    }
	    System.out.println("");
	}
	System.out.println("");
    }

    public static void main(String[] args) {
	int length = 97;
	long[] fakeOids = new long[length];

	for (int i = 0; i < length; i++) {
	    fakeOids[i] = i;
	}

	long[][] clusters;
	try {
	    for (int i = 0; i < 5; i++) {
		clusters = createRelatedClusters(fakeOids, 10, 10, i);
		printClusters(clusters);
		System.out.println("");
	    }
	}
	catch (Exception e) {
	}
    }
}
