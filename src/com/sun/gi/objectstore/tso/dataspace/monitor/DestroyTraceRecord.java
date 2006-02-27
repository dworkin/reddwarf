
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

public class DestroyTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final long id;

    public DestroyTraceRecord(long startTime, long id) {
	super(startTime);
	this.id = id;
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
	long mappedId = replayState.getMappedOid(this.id);

	// XXX: need to actually delete the oid.

	try {
	    dataSpace.destroy(mappedId);
	} catch (NonExistantObjectIDException e) {
	    System.out.println("Contradiction: oid " +  this.id + " not found");
	}
    }

    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
