
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public class LockLogEntry extends LogEntry implements Serializable {
    protected final long id;

    public LockLogEntry(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace) {
	try {
	    dataSpace.lock(id);
	} catch (NonExistantObjectIDException e) {
	    // unexpected;
	}
    }
}
