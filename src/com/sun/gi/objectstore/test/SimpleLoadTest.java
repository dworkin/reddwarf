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

package com.sun.gi.objectstore.test;

import java.io.Serializable;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.TSOObjectStore;
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.PersistantInMemoryDataSpace;

class SLTDataObject implements Serializable {
	int i;

	double d;

	String s;

	public SLTDataObject(int i, double d, String s) {
		this.d = d;
		this.s = s;
		this.i = i;
	}

	public String toString() {
		return "Iint = " + i + ", double = " + d + ", String= " + s;
	}
}

public class SimpleLoadTest {

	private static long[] objids;

	private static boolean DEBUG = true;

	public static void main(String[] args) {
		test(100);
		System.out.println("Warmed up VM, now real tests");
		test(100);
		test(500);
		test(1000);
		test(10000);
	}

	public static void test(int OBJCOUNT) {
		objids = new long[OBJCOUNT];
		ObjectStore ostore = null;
		try {
			ostore = new TSOObjectStore(new PersistantInMemoryDataSpace(1));
		} catch (InstantiationException e) {

			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("Clearing object store");
		ostore.clear();
		System.out.println("Assigning transactions.");
		Transaction t1 = ostore.newTransaction(null);
		t1.start();
		SLTDataObject dobj = new SLTDataObject(55, 3.1415, "data_object");
		int a;
		long start = System.currentTimeMillis();
		// get loop time
		for (int i = 0; i < OBJCOUNT; i++) {
			a = i;
		}
		long looptime = System.currentTimeMillis() - start;
		System.out.println("looptime = " + looptime);
		start = System.currentTimeMillis();
		for (int i = 0; i < OBJCOUNT; i++) {
			objids[i] = t1.create(dobj, null);
		}
		long result = (System.currentTimeMillis() - start) - looptime;
		System.out.println("Milliseconds per create: " + ((float) result)
				/ OBJCOUNT);
		start = System.currentTimeMillis();
		t1.abort();
		System.out.println("Abort time for " + OBJCOUNT + " inserts: "
				+ (System.currentTimeMillis() - start) + " milliseconds.");
		t1 = ostore.newTransaction(null);
		System.out.println("Initializing another transaction");
		t1.start();
		for (int i = 0; i < OBJCOUNT; i++) {
			objids[i] = t1.create(dobj, null);
		}
		start = System.currentTimeMillis();
		t1.commit();
		System.out.println("Commit time for " + OBJCOUNT + " inserts: "
				+ (System.currentTimeMillis() - start) + " milliseconds.");
		t1 = ostore.newTransaction(null);
		t1.start();
		start = System.currentTimeMillis();
		Serializable obj;
		for (int i = 0; i < OBJCOUNT; i++) {
			try {
				obj = t1.peek(objids[i]);
			} catch (NonExistantObjectIDException e) {

				e.printStackTrace();
			}
		}
		result = (System.currentTimeMillis() - start) - looptime;
		System.out.println("Milliseconds per peek: " + ((float) result)
				/ OBJCOUNT);
		start = System.currentTimeMillis();
		t1.commit();
		System.out.println("Commit time for " + OBJCOUNT + " peeks: "
				+ (System.currentTimeMillis() - start) + " milliseconds.");
		t1 = ostore.newTransaction(null);
		t1.start();
		start = System.currentTimeMillis();
		for (int i = 0; i < OBJCOUNT; i++) {
			try {
				obj = t1.lock(objids[i]);
			} catch (DeadlockException e) {

				e.printStackTrace();
			} catch (NonExistantObjectIDException e) {

				e.printStackTrace();
			}
		}
		result = (System.currentTimeMillis() - start) - looptime;
		System.out.println("Milliseconds per lock: " + ((float) result)
				/ OBJCOUNT);
		start = System.currentTimeMillis();
		t1.commit();
		System.out.println("Commit time for " + OBJCOUNT + " locks: "
				+ (System.currentTimeMillis() - start) + " milliseconds.");
		t1 = ostore.newTransaction(null);
		t1.start();
		try {
			obj = t1.lock(objids[0]);
		} catch (DeadlockException e) {

			e.printStackTrace();
		} catch (NonExistantObjectIDException e) {

			e.printStackTrace();
		}
		start = System.currentTimeMillis();
		t1.commit();
		System.out.println("Commit time for " + OBJCOUNT + " writes: "
				+ (System.currentTimeMillis() - start) + " milliseconds.");

		t1 = ostore.newTransaction(null);
		t1.start();
		start = System.currentTimeMillis();
		for (int i = 0; i < OBJCOUNT; i++) {
			try {
				t1.destroy(objids[i]);
			} catch (DeadlockException e) {

				e.printStackTrace();
			} catch (NonExistantObjectIDException e) {

				e.printStackTrace();
			}
		}
		result = (System.currentTimeMillis() - start) - looptime;
		System.out.println("Milliseconds per destroy: " + ((float) result)
				/ OBJCOUNT);
		start = System.currentTimeMillis();
		t1.commit();
		System.out.println("Commit time for " + OBJCOUNT + " destroyss: "
				+ (System.currentTimeMillis() - start) + " milliseconds.");
		ostore.close();
	}

}
