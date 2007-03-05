/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data;

/**
 * Stores information about managed objects that has been modified and need
 * their data stored.
 */
final class FlushInfo {

    /** The amount of additional space to allocate when buffering. */
    private static final int BUFFER = 50;

    /** The object IDs of the modified objects. */
    private long[] oids = new long[BUFFER];

    /** The associated data. */
    private byte[][] dataArray = new byte[BUFFER][];

    /** The number of modified objects. */
    private int count = 0;

    /** Creates an instance. */
    FlushInfo() { }

    /** Adds the object ID and data for a modified object. */
    void add(long oid, byte[] data) {
	if (count == oids.length) {
	    long[] newOids = new long[count + BUFFER];
	    System.arraycopy(oids, 0, newOids, 0, count);
	    oids = newOids;
	    byte[][] newDataArray = new byte[count + BUFFER][];
	    System.arraycopy(dataArray, 0, newDataArray, 0, count);
	    dataArray = newDataArray;
	}
	oids[count] = oid;
	dataArray[count] = data;
	count++;
    }

    /** Returns the object IDs of the modified objects. */
    long[] getOids() {
	if (count == oids.length) {
	    return oids;
	}
	long[] result = new long[count];
	System.arraycopy(oids, 0, result, 0, count);
	return result;
    }

    /** Returns the data of the modified objects. */
    byte[][] getDataArray() {
	if (count == dataArray.length) {
	    return dataArray;
	}
	byte[][] result = new byte[count][];
	System.arraycopy(dataArray, 0, result, 0, count);
	return result;
    }
}
