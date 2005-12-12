/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

/**
 * Generates unique per-instance <code>long</code> identifiers.  <p>
 *
 * The identifiers are not unique in any global sense.  They are
 * unique in the sense that each instance of IdGenerator will only
 * generate (via method <code>nextId</code>) a particular identifier
 * at most once.  (unless the number of identifiers wraps around,
 * should we live long enough to see that.) <p>
 *
 * By default, the identifiers start at zero.  There is also a
 * constructor that creates a IdGenerator that starts at whatever
 * number the invoker specifies.  <p>
 *
 * @author Daniel Ellard
 */

public class IdGenerator {
    private long currId;

    public IdGenerator() {
	currId = 0;
    }

    public IdGenerator(long firstId) {
	currId = firstId;
    }

    synchronized public long nextId() {
	return currId++;
    }
}

