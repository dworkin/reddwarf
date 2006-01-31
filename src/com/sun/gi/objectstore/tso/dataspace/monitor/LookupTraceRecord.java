
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class LookupTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final long id;
    protected final String name;

    public LookupTraceRecord(long startTime, String name, long id) {
	super(startTime);
	this.id = id;
	this.name = name;
    }

    public void replay(DataSpace dataSpace) {
	long id = dataSpace.lookup(name);
	if (id != this.id) {
	    // XXX: ??
	}
    }
    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
