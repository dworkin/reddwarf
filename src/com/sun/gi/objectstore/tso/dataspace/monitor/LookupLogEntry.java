
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public class LookupLogEntry extends LogEntry implements Serializable {
    protected final long id;
    protected final String name;

    public LookupLogEntry(long startTime, String name,
	    long id)
    {
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
}
