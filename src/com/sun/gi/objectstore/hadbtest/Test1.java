/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.HadbDataSpace;

/**
 * @author Daniel Ellard
 */

public class Test1 {

    static public void main(String[] args) {
	try {
	    DataSpace t1 = new HadbDataSpace(1, true);
	    t1.close();
	} catch (Exception e) {
	    System.out.println("Exception: " + e);
	    System.exit(1);
	}

	ObjectStore os = TestUtil.connect(1, false, "hadb", null);
	System.out.println("connected");
	if (os == null) {
	    System.out.println("but os == null");
	    System.exit(1);
	}

	if (TestUtil.sanityCheck(os, "Hello, World", true)) {
	    System.out.println("appears to work");
	}
	else {
	    os.close();
	    System.out.println("yuck");
	}

	try {
	    Thread.sleep(1000);
	} catch (Exception e) {
	}

	if (TestUtil.sanityCheck(os, "Hello, World", true)) {
	    System.out.println("appears to work");
	}
	else {
	    System.out.println("yuck");
	}

	os.close();
    }
}
