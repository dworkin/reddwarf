/*
 */
package com.sun.gi.objectstore.tso.dataspace;

import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.NonExistantObjectIDException;

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
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	long id = dataSpace.getNextID();
	endTime = System.currentTimeMillis();

	return id;
    }

    /**
     * {@inheritDoc}
     */
    public byte[] getObjBytes(long objectID) {
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	byte[] bytes = dataSpace.getObjBytes(objectID);
	endTime = System.currentTimeMillis();

	return bytes;
    }

    /**
     * {@inheritDoc}
     */
    public void lock(long objectID)
	    throws NonExistantObjectIDException
    {
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.lock(objectID);
	endTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    public void release(long objectID) {
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.release(objectID);
	endTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    public void atomicUpdate(boolean clear,
		    Map<String, Long> newNames, Set<Long> deleteSet,
		    Map<Long, byte[]> updateMap, Set insertSet)
	    throws DataSpaceClosedException
    {
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.atomicUpdate(clear, newNames,
		deleteSet, updateMap, insertSet);
	endTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    public Long lookup(String name) {
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	Long oid = dataSpace.lookup(name);
	endTime = System.currentTimeMillis();

	return oid;
    }

    /**
     * {@inheritDoc}
     */
    public long getAppID() {
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	long appId = dataSpace.getAppID();
	endTime = System.currentTimeMillis();

	return appId;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.clear();
	endTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
	long startTime = -1, endTime = -1;

	startTime = System.currentTimeMillis();
	dataSpace.close();
	endTime = System.currentTimeMillis();
    }
}