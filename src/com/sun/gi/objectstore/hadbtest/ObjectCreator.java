/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

/**
 * An object to help populate the database with FillerObjects. <p>
 *
 * @author Daniel Ellard
 */

public class ObjectCreator {
    private final IdGenerator id;
    private int baggageSize;
    private long val = 0;
    private ClassLoader classLoader;
    private final ObjectStore os;
    private String populatorName = "default";

    /**
     * Create an object that helps populate the database by creating
     * new objects and adding them to the database.  <p>
     *
     * @param os the {@link ObjectStore ObjectStore} to which to add
     * the new objects.
     *
     * @param start the "start" of the per-object Id numbers to embed
     * in each object.  These numbers increase sequentially.  These
     * numbers are <em>not</em> related to the OIDs.
     *
     * @param populatorName a string representing the "name" of this
     * populator.  May be null.
     *
     * @param baggageSize the number of bytes occupied by the
     * per-object baggage of each {@link FillerObject FillerObject}.
     */

    public ObjectCreator(ObjectStore os, int start, String populatorName,
	    int baggageSize) {
	this.classLoader = this.getClass().getClassLoader();
	this.id = new IdGenerator(start);
	this.os = os;
	this.populatorName = populatorName;
	this.baggageSize = baggageSize;
    }

    /*
     * A simple wrapper, in case we need to gather more stats here.
     */

    private Transaction beginTransaction(long appId) {

	Transaction trans = os.newTransaction(classLoader);
	return trans;
    }

    /*
     * A simple wrapper, in case we need to gather more stats here.
     */

    private void commitTransaction(Transaction trans) {
	trans.commit();
    }

    /**
     * Create and add a bunch of new objects.  <p>
     *
     * The objects are all added within a single transaction.  <p>
     *
     * @param count the number of new objects to create.
     *
     * @param appId the Id of the owning app.
     *
     * @param selfId whether or not the objects should be backpatched
     * so that the oid field contains a copy of the OID value.
     *
     * @return an array of the OIDs for the new object.
     */

    public long[] createNewBunch(int count, long appId, boolean selfId) {

	if (count <= 0) {
	    return null;	// &&& Sloppy.
	}

	long[] newOIDs = new long[count];

	/*
	 * There's something wrong with adding more than a certain
	 * number of new objects in a single transaction.  So they are
	 * chunked, and we never create more than chunkSize without
	 * committing them.
	 *
	 * Even this may fail, if the individual objects are large
	 * enough.  There is some magic number (around 5MB?) that
	 * Derby transactions must not exceed.
	 */

	int chunkSize = 100;
	for (int base = 0; base < count; base += chunkSize) {
	    System.out.println("chunk base: " + base);
	    Transaction trans = beginTransaction(appId);

	    trans.start();
	    for (int i = 0; i + base < count && i < chunkSize; i++) {
		newOIDs[i + base] = addFillerObj(trans, appId);
	    }

	    commitTransaction(trans);

	    if (selfId) {
		trans.start();
		FillerObject fo;

		for (int i = 0; i + base < count && i < chunkSize; i++) {
		    try {
			fo = (FillerObject) trans.lock(newOIDs[i + base]);
			fo.setOID(newOIDs[i + base]);
		    }
		    catch (Exception e) {
			trans.abort();
			// &&& fix this.
			System.out.println("FAILED TO INITIALIZE");
			System.exit(1);
		    }
		}
		commitTransaction(trans);
	    }
	}


	return newOIDs;
    }

    /**
     * Create and add a single new object.  <p>
     *
     * The object is added within a single transaction.  <p>
     *
     * @param appId the Id of the owning app.
     *
     * @param selfId whether or not the objects should be backpatched
     * so that the oid field contains a copy of the OID value.
     *
     * @return the OID of the new object.
     */

    public long createNew(long appId, boolean selfId) {

	Transaction trans = beginTransaction(appId);

	trans.start();
	long newOID = addFillerObj(trans, appId);

	commitTransaction(trans);

	if (selfId) {
	    selfIdentify(appId, newOID);
	}

	return newOID;
    }

    /**
     * Update the "oid" field with the value of the OID that was
     * assigned to the object.  This self-identifies the object
     * (useful for debugging).
     *
     * @param appId the application ID of the object.
     *
     * @param oid The OID
     *
     * @return <code>true</code> if successful, <code>false</code>
     * otherwise.
     */

    public boolean selfIdentify(long appId, long oid) {
	Transaction trans = beginTransaction(appId);

	trans.start();
	try {
	    FillerObject fo = (FillerObject) trans.lock(oid);
	    fo.setOID(oid);
	    commitTransaction(trans);
	}
	catch (Exception e) {
	    trans.abort();
	    // &&& fix this.
	    return false;
	}

	return true;
    }

    /**
     * Create a new object and add it, within the context of a
     * specific transaction.
     *
     * @param trans the {@link Transaction Transaction}.
     *
     * @param appId the Id of the owning application.
     *
     * @return the OID of the new object.
     */

    protected long addFillerObj(Transaction trans, long appId) {
	long myVal;

	synchronized(this) {
	    myVal = val++;
	}

	FillerObject obj = new FillerObject(id, myVal, baggageSize,
		populatorName, null);

	String name = "appId " + appId + " val " + myVal;
	long oid = trans.create(obj, name);

	/*
	 * &&& should check whether oid is OK.
	 */

	return oid;
    }

    /**
     * Set the size of the per-object "baggage".
     *
     * @param newBaggageSize the new size of the per-object baggage.
     *
     * @return the previous size of the per-object baggage.
     */

    public int setBaggageSize(int newBaggageSize) {
	int oldBaggageSize = baggageSize;
	baggageSize = newBaggageSize;
	return (oldBaggageSize);
    }
}

