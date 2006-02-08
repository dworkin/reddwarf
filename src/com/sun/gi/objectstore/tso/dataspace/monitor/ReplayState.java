/*
 * Copyright 2006, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.tso.dataspace.monitor;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import java.util.Map;
import java.util.HashMap;

/**
 * Simple class to encapsulate the state of a trace replay.  <p>
 *
 * The issue is that the results of some of the replay operations may
 * be different than they were in the trace:  for example, the replay
 * of a create might return a different OID than what appeared in the
 * trace -- and we need to use the <em>new</em> OID in all future ops
 * because otherwise those ops might fail (or do the wrong things).
 */
public class ReplayState {

    private final Map<Long, Long> oidMap = new HashMap<Long, Long>();
    private final Map<String, Long> nameMap = new HashMap<String, Long>();

    public ReplayState() { }

    /**
     * Maps an original OID to the OID to use in the replay.
     *
     * @return <code>true</code> if the mapping is new, <code>false</code>
     * if there is already an existing mapping
     */
    public boolean setOidMap(long origOid, long newOid) {
	if (oidMap.containsKey(origOid)) {
	    oidMap.put(origOid, newOid);
	    System.out.println("\tXX surprising");
	    return false;
	} else {
	    oidMap.put(origOid, newOid);
	    return true;
	}
    }

    public long getMappedOid(long origOid) {
	Long oid = oidMap.get(origOid);
	if (oid == null) {
	    return DataSpace.INVALID_ID;
	} else {
	    return oid;
	}
    }

    /**
     * Maps an original OID name to the OID to use in the replay.
     *
     * @return <code>true</code> if the mapping is new, <code>false</code>
     * if there is already an existing mapping
     */
    public boolean setNameMap(String oidName, long newOid) {
	if (nameMap.containsKey(oidName)) {
	    nameMap.put(oidName, newOid);
	    return false;
	} else {
	    nameMap.put(oidName, newOid);
	    return true;
	}
    }

    public long getMappedOidByName(String name) {
	if (nameMap.containsKey(name)) {
	    return nameMap.get(name);
	} else {
	    return DataSpace.INVALID_ID;
	}
    }
}
