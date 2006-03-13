/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

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

	for (int i = 0; i < locks; i++) {
	    for (int j = i + 1; j < locks; j++) {
		if (results[1][i] == results[1][j]) {
		    System.out.println("DOUBLE LOCKS");
		}
	    }
	    for (int j = 0; j < promotedPeeks; j++) {
		if (results[1][i] == results[2][j]) {
		    System.out.println("DOUBLE LOCK/PROMOTED");
		}
	    }
	}
	for (int i = 0; i < promotedPeeks; i++) {
	    for (int j = i + 1; j < promotedPeeks; j++) {
		if (results[2][i] == results[2][j]) {
		    System.out.println("DOUBLE PROMOTED");
		}
	    }
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
