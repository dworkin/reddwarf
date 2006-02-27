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
	byte[] buf = new byte[10];
	long id1 = 0;

	try {
	    // t1 = new HadbTest(1);
	    t1 = new HadbDataSpace(1, false);
	} catch (Exception e) {
	    System.out.println("surrender");
	    System.exit(1);
	}

	for (int i = 0; i < 1000; i++) {
	    id1 = t1.create(buf, null);
	    System.out.println("id1: " + id1 + " " + identifier);
	}

	try {
	    t1.lock(id1);
	    t1.release(id1);
	} catch (Exception e) {
	    System.out.println(e);
	}
    }
}
