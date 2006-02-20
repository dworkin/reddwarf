/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import java.io.Serializable;

/**
 * An object whose sole purpose is to exercise the ObjectStore. <p>
 *
 * A FillerObject has a few fields that serve to identify it, plus a
 * few extra fields that allow it to consume meaningless space in a
 * somewhat parameterized manner.  <p>
 *
 * @author Daniel Ellard
 */

public class FillerObject implements Serializable {
    static final long serialVersionUID = 0L;

    private long id;
    private byte[] baggage;
    private long oid;

    private FillerObject() {
	id = -1;
	baggage = null;
	oid = -1;
    }

    /**
     * @param id an IdGenerator responsible for uniquifying this
     * object.
     *
     * @param size the length of a byte array to allocate.  If this
     * number is less than 0, no array is allocated, but it is not an
     * error.
     */
    public FillerObject(IdGenerator id, int size) {
	this.id = id.nextId();
	if (size >= 0) {
	    this.baggage = new byte[size];
	}
	else {
	    this.baggage = null;
	}
	this.oid = -1; // purposefully bogus
    }

    /**
     * Set the oid field of the object to a specific value. The
     * intent is that this is the actual OID chosen by the ObjectStore.
     * The problem is that that OID is not known until a persistant
     * version of this object is created, and therefore it cannot
     * be assigned until that time.
     */
    public void setOID(long oid) {
	this.oid = oid;
    }

    public long getOID() {
	return oid;
    }

    public long getId() {
	return id;
    }

    public byte[] getBaggage() {
	return baggage;
    }
}

