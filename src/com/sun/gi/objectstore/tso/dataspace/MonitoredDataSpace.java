/*
 */
package com.sun.gi.objectstore.tso.dataspace;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.dataspace.monitor.AtomicUpdateTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.ClearTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.CloseTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.GetAppIdTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.GetNextIdTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.GetObjBytesTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.LockTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.LookupTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.ReleaseTraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.TraceRecord;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Set;

/**
 * Simple wrapper for a {@link DataSpace} implementation that gathers
 * a trace of its operation.
 */
public class MonitoredDataSpace implements DataSpace {
    private final DataSpace dataSpace;
    private volatile boolean loggingEnabled = false;
    private volatile ObjectOutputStream entryLog;

    /**
     * Simple, degenerate constructor that creates a wrapper that
     * does <em>not</em> gather any traces.
     *
     * @param dataSpace the wrapped {@link DataSpace}
     *
     * @throws NullPointerException if dataSpace is <code>null</code>
     */
    public MonitoredDataSpace(DataSpace dataSpace) {
	if (dataSpace == null) {
	    throw new NullPointerException("dataSpace is null");
	}

	this.dataSpace = dataSpace;
	this.loggingEnabled = false;
	this.entryLog = null;
    }

    /**
     * Creates a wrapper that captures traces of the operations
     * performed by a {@link DataSpace}. <p>
     *
     * If the trace file already exists, it is truncated and overwritten.
     *
     * @param dataSpace the {@link DataSpace} to trace
     *
     * @param traceFileName the path to the file in which to store the
     * trace
     *
     * @throws NullPointerException if dataSpace is <code>null</code>
     *
     * @throws IOException if there is an IO error with the trace file
     */
    public MonitoredDataSpace(DataSpace dataSpace, String traceFileName)
	    throws IOException
    {
	if (dataSpace == null) {
	    throw new NullPointerException("dataSpace is null");
	}

	this.dataSpace = dataSpace;

	FileOutputStream fos = new FileOutputStream(pathName);
	ObjectOutputStream oos = new ObjectOutputStream(fos);

	this.entryLog = oos;
	this.loggingEnabled = true;
    }

    /**
     * {@inheritDoc}
     */
    public long getNextID() {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	long id = dataSpace.getNextID();

	if (loggingEnabled()) {
	    log(new GetNextIdTraceRecord(startTime, id));
	}

	return id;
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getObjBytes(long objectID) {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	byte[] bytes = dataSpace.getObjBytes(objectID);

	if (loggingEnabled()) {
	    log(new GetObjBytesTraceRecord(startTime, objectID, bytes.length));
	}

	return bytes;
    }

    /**
     * {@inheritDoc}
     */
    public void lock(long objectID)
	    throws NonExistantObjectIDException
    {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	dataSpace.lock(objectID);

	if (loggingEnabled()) {
	    log(new LockTraceRecord(startTime, objectID));
	}
    }

    /**
     * {@inheritDoc}
     */
    public void release(long objectID) {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	dataSpace.release(objectID);

	if (loggingEnabled()) {
	    log(new ReleaseTraceRecord(startTime, objectID));
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
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	dataSpace.atomicUpdate(clear, newNames,
		deleteSet, updateMap, insertSet);

	if (loggingEnabled()) {
	    log(new AtomicUpdateTraceRecord(startTime, clear, newNames,
		    deleteSet, updateMap, insertSet));
	}
    }

    /**
     * {@inheritDoc}
     */
    public Long lookup(String name) {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	Long oid = dataSpace.lookup(name);

	if (loggingEnabled()) {
	    log(new LookupTraceRecord(startTime, name, oid));
	}

	return oid;
    }

    /**
     * {@inheritDoc}
     */
    public long getAppID() {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	long appId = dataSpace.getAppID();

	if (loggingEnabled()) {
	    log(new GetAppIdTraceRecord(startTime, appId));
	}

	return appId;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	dataSpace.clear();

	if (loggingEnabled()) {
	    log(new ClearTraceRecord(startTime));
	}
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	dataSpace.close();

	if (loggingEnabled()) {
	    log(new CloseTraceRecord(startTime));
	    closeLog();
	}
    }

    /**
     * Enables/disables logging.
     *
     * @param val enable logging if <code>true</code>, disable
     * logging if <code>false</code>
     */
    public void enableLogging(boolean val) {
	loggingEnabled = val;
    }

    /**
     * Indicates whether logging is currently enabled.
     *
     * @return <code>true<code> if logging is enabled, <code>false</code
     * otherwise
     */
    public boolean loggingEnabled() {
	return loggingEnabled;
    }

    private synchronized void closeLog() {

	if (!loggingEnabled() || (entryLog == null)) {
	    return;
	}

	try {
	    entryLog.flush();
	    entryLog.close();
	} catch (IOException e) {
	    // XXX: should do something intelligent.  Doesn't.
	}

	entryLog = null;
	enableLogging(false);
    }

    /**
     * Logs a {@link TraceRecord} to the trace file.
     *
     * @param entry the {@link TraceRecord} to store
     */
    private synchronized void log(TraceRecord entry) {
	if (loggingEnabled() && (entryLog != null)) {
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
		enableLogging(false);
	    }
	}
    }
}