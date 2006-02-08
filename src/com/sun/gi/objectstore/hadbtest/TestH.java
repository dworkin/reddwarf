/*
 * <p>Copyright: Copyright (c) 2006 Sun Microsystems, Inc.</p>
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.HadbDataSpace;

public class TestH {

    public static void main(String[] args) {
	String identifier = args[0];
	// HadbTest t1 = null;
	DataSpace t1 = null;

	try {
	    // t1 = new HadbTest(1);
	    t1 = new HadbDataSpace(1, true);
	} catch (Exception e) {
	    System.out.println("surrender");
	    System.exit(1);
	}

	for (int i = 0; i < 100; i++) {
	    long id1 = t1.getNextID();
	    System.out.println("id1: " + id1 + " " + identifier);
	}

	long testId = t1.getNextID();
	try {
	    t1.lock(testId);
	    t1.release(testId);
	} catch (Exception e) {
	    System.out.println(e);
	}
    }
}
