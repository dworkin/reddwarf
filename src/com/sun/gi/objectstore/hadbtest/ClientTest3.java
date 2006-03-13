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

class ClientTest3 implements Runnable {
    private ObjectStore os;
    private final long clientId;
    private long lastWake;
    private long mySleep = 50;
    private long iters = 5000;
    private long[][] oidClusters;
    private int numPeeks = 4;
    private int numLocks = 2;
    private int numPromotedPeeks = 1;
    private Random r = new Random();
    private boolean verbose = false;
    private long sleepTime;

    public ClientTest3(long clientId, ObjectStore os, long[][] oidClusters,
	    long sleepTime) {
	this.os = os;
	this.clientId = clientId;
	this.oidClusters = oidClusters;
	this.sleepTime = sleepTime;
    }

    public void run() {
	System.out.println("starting up " + clientId + ": whoopee");
	lastWake = System.currentTimeMillis();

	long start = System.currentTimeMillis();
	for (int i = 0; i < iters; i++) {

	    if (i % 100 == 0) {
		System.out.println("snoozing at count " + i);
		try {
		    Thread.sleep(sleepTime);
		} catch (Exception e) {
		}

		System.out.println("snoozing at count " + i);
	    }

	    if (i % 50 == 0) {
		System.out.println("at count " + i);
	    }

	    if ((i > 0) && ((i % 1000) == 0)) {
		long now = System.currentTimeMillis();
		long elapsed = start - now;
		start = now;
		System.out.println("elapsed for 1000: " + elapsed);
	    }
	    try {
		doRandomTransaction(verbose);
	    } catch (Exception e) {
		// DJE:
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

	trans.commit();
    }
}
