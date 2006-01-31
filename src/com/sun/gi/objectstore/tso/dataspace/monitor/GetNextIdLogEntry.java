
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public class GetNextIdLogEntry extends LogEntry implements Serializable {
    protected final long id;

    public GetNextIdLogEntry(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace) {
	long id = dataSpace.getNextID();
	if (id != this.id) {
	    // XXX ??
	}
    }
}
