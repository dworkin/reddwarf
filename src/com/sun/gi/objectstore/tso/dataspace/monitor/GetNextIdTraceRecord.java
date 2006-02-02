
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class GetNextIdTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final long id;

    public GetNextIdTraceRecord(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
	// OK.
	long newId = dataSpace.getNextID();

	if (!replayState.setOidMap(this.id, newId)) {
	    System.out.println("Contradiction: old = " + this.id +
		    " new = " + newId +
		    " prev = " + replayState.getMappedOid(this.id));
	}
    }
    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
