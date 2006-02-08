package com.sun.gi.objectstore.hadbtest;

import java.io.Serializable;

class SimpleTestObj implements Serializable {
    public final long oid;
    public final String val;

    public SimpleTestObj(long oid, String val) {
	this.oid = oid;
	this.val = val;
    }
}

