/*
 * Copyright: Copyright (c) 2006 Sun Microsystems, Inc.
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.tso.dataspace.HadbDataSpace;

/**
 * Utility to clear and reinitialize an app's tables.
 */
public class ResetHadb {

    /**
     * Clears and reinitializes the dataspace of the app given on the
     * commandline.  This will destroy all of the data belonging to
     * that app in the dataspace.  <p>
     *
     * This should only be done when the database is idle.  If the app
     * is still running, this will pull the rug out from underneath it
     * in a dramatic, catastrophic manner.  <p>
     */
    public static void main(String[] args) {
	if (args.length != 1) {
	    System.out.println("INCORRECT USAGE");
	    System.out.println("Usage: " + "progName" + " appID");
	    System.exit(1);
	}

	long appID = (long) new Integer(args[0]);
	HadbDataSpace t1 = null;

	try {
	    t1 = new HadbDataSpace(appID, true);
	    t1.close();
	} catch (Exception e) {
	    System.out.println("FAILED");
	    System.exit(1);
	}
    }
}
