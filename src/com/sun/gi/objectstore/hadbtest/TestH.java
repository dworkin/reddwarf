/*
 * <p>Copyright: Copyright (c) 2006 Sun Microsystems, Inc.</p>
 */

package com.sun.gi.objectstore.hadbtest;

public class TestH {

    public static void main(String[] args) {
	HadbTest t1 = null;

	try {
	    t1 = new HadbTest(1);
	} catch (Exception e) {
	    System.out.println("surrender");
	    System.exit(1);
	}

	for (int i = 0; i < 5000; i++) {
	    long id1 = t1.getNextID();
	    System.out.println("id1: " + id1);
	}
    }
}
