/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

/**
 * @author Daniel Ellard
 */

public class TestPersistance {

    static public void main(String[] args) {
	int count = 100;

	long[] oids = testWrite(count);

	if (!testReread(oids)) {
	    System.out.println("FAILED");
	} else {
	    System.out.println("passed");
	}
    }

    public static long[] testWrite(int count) {
	long oids[] = new long[count];

	ObjectStore os = TestUtil.connect(false);
	os.clear();

	Transaction trans1 = os.newTransaction(null);
	trans1.start();

	for (int i = 0; i < count; i++) {
	    oids[i] = trans1.create(new SimpleTestObj(i, "" + i), null);
	}

	trans1.commit();

	os.close();

	return oids;
    }

    public static boolean testReread(long[] oids) {

	// Re-open the database, see what's there.

	ObjectStore os = TestUtil.connect(false);

	Transaction trans2 = os.newTransaction(null);
	boolean passed = true;
	trans2.start();

	for (int i = 0; i < oids.length; i++) {
	    try {
		SimpleTestObj obj = (SimpleTestObj) trans2.peek(oids[i]);
		if (obj.oid != (long) i) {
		    System.out.println("\tmismatch " + obj.oid + " vs " + oids[i]);
		    passed = false;
		}
	    } catch (Exception e) {
		System.out.println("FAILED: " + i + " " + oids[i] + " " + e);
		return false;
	    }
	}

	trans2.abort();
	os.close();

	return passed;
    }
}

