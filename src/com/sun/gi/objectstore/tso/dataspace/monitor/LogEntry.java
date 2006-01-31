
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;

public abstract class LogEntry implements Serializable {
    private long startTime;
    private long endTime;

    protected LogEntry() { }

    public LogEntry(long startTime) {
	this.startTime = startTime;
	this.endTime = System.currentTimeMillis();

	System.out.println("\t" + this.startTime + " " + this.endTime);
    }

    public long getStartTime() {
	return startTime;
    }

    public long getEndTime() {
	return endTime;
    }

    public abstract void replay(DataSpace dataSpace);
}
