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
	ObjectStore os = TestUtil.connect(true);

	if (TestUtil.sanityCheck(os, "Hello, World", true)) {
	    System.out.println("appears to work");
	}
	else {
	    System.out.println("yuck");
	}
    }
}
