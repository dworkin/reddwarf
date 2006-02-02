
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class ClearTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private ClearTraceRecord() { }

    public ClearTraceRecord(long startTime) {
	super(startTime);
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
	// OK.
	dataSpace.clear();
    }

    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
