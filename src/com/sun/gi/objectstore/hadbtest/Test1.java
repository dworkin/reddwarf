/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;

/**
 * @author Daniel Ellard
 */

public class Test1 {

    static public void main(String[] args) {
	ObjectStore os = TestUtil.connect(1, true, "hadb", "Hlog1");

	System.out.println("connected");
	if (os == null) {
	    System.out.println("but os == null");
	    System.exit(1);
	}

	if (TestUtil.sanityCheck(os, "Hello, World", true)) {
	    System.out.println("appears to work");
	}
	else {
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
