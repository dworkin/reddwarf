
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class GetObjBytesLogEntry implements LogEntry, Serializable {
    private static final long serialVersionUID = 1L;
    private final long startTime;
    private final long endTime;
    protected final long id;
    protected final int length;

    public GetObjBytesLogEntry(long startTime, long id, int length)
    {
	this.startTime = startTime;
	this.endTime = System.currentTimeMillis();
	this.id = id;
	this.length = length;
    }

    public long getStartTime() {
	return startTime;
    }

    public long getEndTime() {
	return endTime;
    }


    public void replay(DataSpace dataSpace) {
	byte[] res = dataSpace.getObjBytes(id);
	if (res.length != length) {
	    // XXX ??
	}
    }
    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
