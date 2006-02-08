/*
 * Copyright: Copyright (c) 2006 Sun Microsystems, Inc.
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.tso.dataspace.HadbDataSpace;

public class ResetHadb {

    public static void main(String[] args) {
	long appID = (long) new Integer(args[0]);
	HadbDataSpace t1 = null;

	try {
	    t1 = new HadbDataSpace(appID, true);
	} catch (Exception e) {
	    System.out.println("surrender");
	    System.exit(1);
	}
    }
}
