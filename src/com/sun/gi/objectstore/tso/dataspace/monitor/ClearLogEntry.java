
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class ClearLogEntry implements LogEntry, Serializable {
    private static final long serialVersionUID = 1L;
    private final long startTime;
    private final long endTime;

    public ClearLogEntry(long startTime) {
	this.startTime = startTime;
	this.endTime = System.currentTimeMillis();
    }

    public long getStartTime() {
	return startTime;
    }

    public long getEndTime() {
	return endTime;
    }


    public void replay(DataSpace dataSpace) {
	dataSpace.clear();
    }
    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
