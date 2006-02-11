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
    private long value;
    private byte[] baggage;
    private int bagLength;
    private String text;
    private Serializable obj;
    private long oid;

    private FillerObject() {
	id = -1;
	value = -2;
	baggage = null;
	bagLength = -3;
	text = "bogus";
	obj = null;
	oid = -1;
    }

    /**
     * @param id an IdGenerator responsible for uniquifying this
     * object.
     *
     * @param value a value assigned to this object.  The semantics
     * are undefined.
     *
     * @param size the length of a byte array to allocate.  If this
     * number is less than 0, no array is allocated, but it is not an
     * error.
     *
     * @param text a String referenced by the object.  It may be null.
     *
     * @param obj an arbitrary object referenced by the object.  It
     * must be serializable.  It may be null.  The object referenced
     * is not cloned or otherwise copied.
     */

    public FillerObject(IdGenerator id, long value, int size, String text,
    	    Serializable obj) {
	this.id = id.nextId();
	this.value = value;
	if (size >= 0) {
	    this.baggage = new byte[size];
	}
	else {
	    this.baggage = null;
	}
	this.bagLength = size; // for no good reason; just another field.
	this.text = text;
	this.obj = obj;
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

    public long getValue() {
	return value;
    }

    public byte[] getBaggage() {
	return baggage;
    }

    public int getBagLength() {
	return bagLength;
    }

    public String getText() {
	return text;
    }

    public Serializable getObj() {
	return obj;
    }
}

