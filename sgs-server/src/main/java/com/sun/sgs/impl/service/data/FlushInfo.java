/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * --
 */

package com.sun.sgs.impl.service.data;

/**
 * Stores information about managed objects that have been modified and need
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
