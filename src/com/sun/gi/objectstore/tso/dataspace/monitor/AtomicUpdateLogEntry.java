
package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.DataSpaceClosedException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AtomicUpdateLogEntry extends LogEntry implements Serializable {
    protected final boolean clear;
    protected final Map<String, Long> newNames;
    protected final Set<Long> deleteSet;
    protected final Map<Long, Integer> updateMap;
    protected final Set<Long> insertSet;

    public AtomicUpdateLogEntry(long startTime, boolean clear,
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
}
