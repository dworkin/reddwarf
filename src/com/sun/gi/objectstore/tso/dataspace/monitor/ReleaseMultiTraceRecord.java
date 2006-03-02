
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashSet;
import java.util.Set;

public class ReleaseMultiTraceRecord extends TraceRecord
	implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected final Set<Long> ids;

    public ReleaseMultiTraceRecord(long startTime, Set<Long> ids) {
	super(startTime);
	this.ids = new HashSet<Long>(ids);
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {
	Set<Long> mappedIds = new HashSet<Long>();
	for (long oid : ids) {
	    long mappedId = replayState.getMappedOid(oid);
	    mappedIds.add(mappedId);
	}

	try {
	    dataSpace.release(mappedIds);
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
