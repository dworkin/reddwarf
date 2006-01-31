
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
    private static final long serialVersionUID = 1L;
    protected final boolean clear;
    protected final Map<String, Long> newNames;
    protected final Set<Long> deleteSet;
    protected final Map<Long, Integer> updateMap;
    protected final Set<Long> insertSet;

    public AtomicUpdateTraceRecord(long startTime, boolean clear,
	    Map<String, Long> newNames, Set<Long> deleteSet,
	    Map<Long, byte[]> updateMap, Set<Long> insertSet)
    {
	super(startTime);

	this.clear = clear;
	this.newNames = new HashMap<String, Long>(newNames);
	this.deleteSet = new HashSet<Long>(deleteSet);
	this.insertSet = new HashSet<Long>(insertSet);
	this.updateMap = new HashMap<Long, Integer>();
	for (Long oid : updateMap.keySet()) {
	    this.updateMap.put(oid, new Integer(updateMap.get(oid).length));
	}
    }

    public void replay(DataSpace dataSpace) {
	Map<Long, byte[]> dummys = new HashMap<Long, byte[]>();

	for (Long oid : updateMap.keySet()) {
	    dummys.put(oid, new byte[updateMap.get(oid)]);
	}

	try {
	    dataSpace.atomicUpdate(clear, newNames, deleteSet,
		    dummys, insertSet);
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
