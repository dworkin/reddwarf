
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class ReleaseTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final long id;

    public ReleaseTraceRecord(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
	// OK.
	long mappedId = replayState.getMappedOid(this.id);
	// check?

	try {
	    dataSpace.release(mappedId);
	} catch (NonExistantObjectIDException e) {
	    // XXX: make note of the error
	}
    }

    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
