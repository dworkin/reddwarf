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
import com.sun.gi.objectstore.tso.dataspace.monitor.NewNameTraceRecord;
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

	FileOutputStream fos = new FileOutputStream(traceFileName);
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

	// TODO sten commented out to fix build
	
	//dataSpace.atomicUpdate(clear, newNames,
	//	deleteSet, updateMap, insertSet);

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
     * {@inheritDoc}
     */
    public boolean newName(String name) {
	long startTime = loggingEnabled() ? System.currentTimeMillis() : -1;

	boolean rc = true; /*dataSpace.newName(name);*/  // TODO sten commented out to fix build

	if (loggingEnabled()) {
	    log(new NewNameTraceRecord(startTime, name));
	}

	return rc;
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

	    /*
	     * Note:  it is not expected that the objects will not
	     * reference each other, so the stream is reset after
	     * each.  If it is not reset periodically, the
	     * ObjectOutputStream will keep a reference to everything
	     * it has ever written, and quickly sponge up all of
	     * memory.
	     */

	    try {
		entryLog.writeObject(entry);
		entryLog.reset();
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
    
    // TODO: Sten inserted stubs to fix the build -- someone implement
    
    /**
     * Atomically updates the DataSpace.  <p>
     *
     * The <code>updateMap</code> contains new bindings between object
     * identifiers and the byte arrays that represent their values. 
     *
     * @param clear <b>NOT USED IN CURRENT IMPL</b>
     *
     * @param updateMap new bindings between object identifiers and
     * byte arrays
     *
     * @throws DataSpaceClosedException
     *
     * (What is <code>clear</code> supposed to do?  Or is this now
     * unused and should be removed?  -DJE)
     */
    public void atomicUpdate(boolean clear, Map<Long, byte[]> updateMap)
	    throws DataSpaceClosedException {
    	
    }
    
    /** creates a new element in the DataSpace
     * If name is non-null and the name is already in the DataSpace then
     * create will fail.  
     * 
     * Create is an immediate (non-transactional) chnage to the DataSpace.
     * 
     * @return objectID or DataSpace.Invalid_ID if it fails
     */
    
    public long create(byte[] data,String name) {
    	return 0;
    }
    
	/**
	 * Destroys the object associated with objectID and removes the name
	 * associated with that ID (if any.)
	 * 
	 * destroy is an immediate (non-transactional) change to the DataSpace.
	 * 
	 * @param objectID
	 */
	public void destroy(long objectID) {
		
	}

}