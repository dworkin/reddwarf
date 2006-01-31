
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public class ReleaseLogEntry extends LogEntry implements Serializable {
    protected final long id;

    public ReleaseLogEntry(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace) {
	dataSpace.release(id);
    }
}
