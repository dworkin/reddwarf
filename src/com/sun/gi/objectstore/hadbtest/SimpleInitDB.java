/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;

/**
 * @author Daniel Ellard
 */

public class SimpleInitDB {

    /**
     * @param os the ObjectStore in which to store new {@link
     * FillerObject FillerObjects} instances.
     *
     * @param appId the "owning app" of the objects.
     *
     * @param start the seed for the "value" of the first object.  The
     * values are incremented for each subsequent object created by
     * this batch.
     *
     * @param name the String used as the value of the name field of
     * each object.
     *
     * @param count the number of objects to create.
     *
     * @param size the size of the "baggage" (aka the payload) of the
     * object.  This controls how much space each object takes in the
     * database.
     *
     * @return an array containing the ObjectStore OIDs of each new
     * object.
     */

    public static long[] createObjects(ObjectStore os, long appId, int start,
	    String name, int count, int size) {
	ObjectCreator oc = new ObjectCreator(os, start, name);

	return oc.createNewBunch(count, size, appId, true);
    }

}
