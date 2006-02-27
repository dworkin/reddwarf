
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.DataSpaceClosedException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.io.ObjectInputStream;

public class AtomicUpdateTraceRecord extends TraceRecord implements Serializable {
    private static final long serialVersionUID = 2L;
    protected final boolean clear;
    protected final Map<Long, Integer> updateMap;

    public AtomicUpdateTraceRecord(long startTime, boolean clear,
	    Map<Long, byte[]> updateMap)
    {
	super(startTime);

	this.clear = clear;
	this.updateMap = new HashMap<Long, Integer>();
	for (Long oid : updateMap.keySet()) {
	    this.updateMap.put(oid, new Integer(updateMap.get(oid).length));
	}
    }

    public void replay(DataSpace dataSpace, ReplayState replayState) {

	Map<Long, byte[]> mappedUpdateMap = new HashMap<Long, byte[]>();
	for (Long oid : updateMap.keySet()) {
	    long mappedOid = replayState.getMappedOid(oid);
	    if (mappedOid == DataSpace.INVALID_ID) {
		System.out.println("Unknown updated OID in atomicUpdate replay");
	    } else {
		mappedUpdateMap.put(mappedOid, new byte[updateMap.get(oid)]);
	    }
	}

	try {
	    dataSpace.atomicUpdate(clear, mappedUpdateMap);
	} catch (DataSpaceClosedException e) {
	    // XXX: unexpected
	}
    }

    private void readObject(ObjectInputStream in)   
	    throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
    }
}
