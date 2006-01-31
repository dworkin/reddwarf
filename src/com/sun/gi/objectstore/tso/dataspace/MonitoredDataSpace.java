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
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

/**
 */
public class MonitoredDataSpace implements DataSpace {
    private final DataSpace dataSpace;
    private boolean loggingEnabled = false;
    private ObjectOutputStream entryLog;

    public MonitoredDataSpace(DataSpace dataSpace) {
	if (dataSpace == null) {
	    throw new NullPointerException("dataSpace is null");
	}

	this.dataSpace = dataSpace;
	this.loggingEnabled = false;
	this.entryLog = null;
    }

    public MonitoredDataSpace(DataSpace dataSpace, String pathName)
	    throws IOException
    {
	if (dataSpace == null) {
	    throw new NullPointerException("dataSpace is null");
	}

	this.dataSpace = dataSpace;

	FileOutputStream fos = new FileOutputStream(pathName, true);
	ObjectOutputStream oos = new ObjectOutputStream(fos);

	this.entryLog = oos;
	this.loggingEnabled = true;
    }

    /**
     * {@inheritDoc}
     */
    public long getNextID() {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	long id = dataSpace.getNextID();

	if (loggingEnabled) {
	    log(new GetNextIdLogEntry(startTime, id));
	}

	return id;
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getObjBytes(long objectID) {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	byte[] bytes = dataSpace.getObjBytes(objectID);

	if (loggingEnabled) {
	    log(new GetObjBytesLogEntry(startTime, objectID, bytes.length));
	}

	return bytes;
    }

    /**
     * {@inheritDoc}
     */
    public void lock(long objectID)
	    throws NonExistantObjectIDException
    {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	dataSpace.lock(objectID);

	if (loggingEnabled) {
	    log(new LockLogEntry(startTime, objectID));
	}
    }

    /**
     * {@inheritDoc}
     */
    public void release(long objectID) {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	dataSpace.release(objectID);

	if (loggingEnabled) {
	    log(new ReleaseLogEntry(startTime, objectID));
	}
    }

    /**
     * {@inheritDoc}
     */
    public void atomicUpdate(boolean clear,
	    Map<String, Long> newNames, Set<Long> deleteSet,
	    Map<Long, byte[]> updateMap, Set<Long> insertSet)
	throws DataSpaceClosedException
    {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	dataSpace.atomicUpdate(clear, newNames,
		deleteSet, updateMap, insertSet);

	if (loggingEnabled) {
	    log(new AtomicUpdateLogEntry(startTime, clear, newNames,
		    deleteSet, updateMap, insertSet));
	}
    }

    /**
     * {@inheritDoc}
     */
    public Long lookup(String name) {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	Long oid = dataSpace.lookup(name);

	if (loggingEnabled) {
	    log(new LookupLogEntry(startTime, name, oid));
	}

	return oid;
    }

    /**
     * {@inheritDoc}
     */
    public long getAppID() {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	long appId = dataSpace.getAppID();

	if (loggingEnabled) {
	    log(new GetAppIdLogEntry(startTime, appId));
	}

	return appId;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	dataSpace.clear();

	if (loggingEnabled) {
	    log(new ClearLogEntry(startTime));
	}
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
	long startTime = loggingEnabled ? System.currentTimeMillis() : -1;

	dataSpace.close();

	if (loggingEnabled) {
	    log(new CloseLogEntry(startTime));
	}
    }

    public void loggingEnabled(boolean val) {
	loggingEnabled = val;
    }

    public boolean loggingEnabled() {
	return loggingEnabled;
    }

    private synchronized void log(LogEntry entry) {
	if (loggingEnabled && (entryLog != null)) {
	    try {
		entryLog.writeObject(entry);
		entryLog.flush();
	    } catch (IOException e) {

		// XXX: should do something intelligent.  Doesn't.

		try {
		    entryLog.close();
		} catch (IOException e2) {
		    // surrender
		}

		entryLog = null;
		loggingEnabled = false;
	    }
	}
    }
}