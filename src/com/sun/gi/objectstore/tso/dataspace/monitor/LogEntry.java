
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public abstract class LogEntry {
    protected final long startTime;
    protected final long endTime;

    public LogEntry(long startTime) {
	this.startTime = startTime;
	this.endTime = System.currentTimeMillis();;
    }

    public abstract void replay(DataSpace dataSpace);
}
