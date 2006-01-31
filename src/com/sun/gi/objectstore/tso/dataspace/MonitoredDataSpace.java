/*
 */
package com.sun.gi.objectstore.tso.dataspace;

import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.dataspace.monitor.AtomicUpdateLogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.ClearLogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.CloseLogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.GetAppIdLogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.GetNextIdLogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.GetObjBytesLogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.LockLogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.LogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.LookupLogEntry;
import com.sun.gi.objectstore.tso.dataspace.monitor.ReleaseLogEntry;

/**
 */
public class MonitoredDataSpace implements DataSpace {
    private final DataSpace dataSpace;

    public MonitoredDataSpace(DataSpace dataSpace) {
	this.dataSpace = dataSpace;
    }

    /**
     * {@inheritDoc}
     */
    public long getNextID() {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	long id = dataSpace.getNextID();

	log(new GetNextIdLogEntry(startTime, id));

	return id;
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getObjBytes(long objectID) {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	byte[] bytes = dataSpace.getObjBytes(objectID);

	log(new GetObjBytesLogEntry(startTime, objectID, bytes.length));

	return bytes;
    }

    /**
     * {@inheritDoc}
     */
    public void lock(long objectID)
	    throws NonExistantObjectIDException
    {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.lock(objectID);

	log(new LockLogEntry(startTime, objectID));
    }

    /**
     * {@inheritDoc}
     */
    public void release(long objectID) {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.release(objectID);

	log(new ReleaseLogEntry(startTime, objectID));
    }

    /**
     * {@inheritDoc}
     */
    public void atomicUpdate(boolean clear,
		    Map<String, Long> newNames, Set<Long> deleteSet,
		    Map<Long, byte[]> updateMap, Set insertSet)
	    throws DataSpaceClosedException
    {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.atomicUpdate(clear, newNames,
		deleteSet, updateMap, insertSet);

	log(new AtomicUpdateLogEntry(startTime, clear, newNames,
		deleteSet, updateMap, insertSet));
    }

    /**
     * {@inheritDoc}
     */
    public Long lookup(String name) {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	Long oid = dataSpace.lookup(name);

	log(new LookupLogEntry(startTime, name, oid));
	return oid;
    }

    /**
     * {@inheritDoc}
     */
    public long getAppID() {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	long appId = dataSpace.getAppID();

	log(new GetAppIdLogEntry(startTime, appId));

	return appId;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.clear();

	log(new ClearLogEntry(startTime));
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
	long startTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.close();

	log(new CloseLogEntry(startTime));
    }

    private void log(LogEntry entry) {
	// synchronized (
    }
}