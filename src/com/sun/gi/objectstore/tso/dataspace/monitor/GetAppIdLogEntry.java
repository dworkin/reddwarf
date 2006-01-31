
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public class GetAppIdLogEntry extends LogEntry implements Serializable {
    protected final long id;

    public GetAppIdLogEntry(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace) {
	long id = dataSpace.getAppID();
	if (id != this.id) {
	    // XXX: ??
	}
    }
}
