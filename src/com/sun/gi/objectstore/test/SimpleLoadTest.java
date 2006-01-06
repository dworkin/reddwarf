package com.sun.gi.objectstore.test;

import java.io.Serializable;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.TSOObjectStore;
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.PersistantInMemoryDataSpace;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */

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
