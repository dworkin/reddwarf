/**
 * <p>Title: InMemoryDataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso.dataspace;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.NonExistantObjectIDException;

/**
 * 
 * <p>
 * Title: InMemoryDataSpace.java
 * </p>
 * <p>
 * Description: This is a version of the InMemoryDataSpace that asynchronously backs itself
 * up to a Derby on-disc database.  
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class InMemoryDataSpace implements DataSpace { 
	long appID;
	Map<Long, byte[]> dataSpace = new LinkedHashMap<Long, byte[]>();

	Map<String, Long> nameSpace = new LinkedHashMap<String, Long>();

	Set<Long> lockSet = new HashSet<Long>();

	private Object idMutex = new Object();

	private int id = 1;

	public InMemoryDataSpace(long appID) {
		this.appID = appID;
	}

	// internal routines to the system, used by transactions
	/*
	 */
	private long getNextID() {
		synchronized (idMutex) {
			return id++;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public byte[] getObjBytes(long objectID) {		
		synchronized (dataSpace) {
			return dataSpace.get(new Long(objectID));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void lock(long objectID)
			throws NonExistantObjectIDException {
		
		synchronized (dataSpace) {
			if (!dataSpace.containsKey(objectID)) {
				throw new NonExistantObjectIDException();
			}
		}		
		synchronized (lockSet) {
			while (lockSet.contains(objectID)) {
				try {
					lockSet.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			lockSet.add(new Long(objectID));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void release(long objectID)
			throws NonExistantObjectIDException
	{
		synchronized (lockSet) {
			lockSet.remove(new Long(objectID));
			lockSet.notifyAll();
		}

	}

	/**
	 * {@inheritDoc}
	 */
	public void release(Set<Long> objectIDs)
		throws NonExistantObjectIDException
	{
	    NonExistantObjectIDException re = null;

	    for (long oid : objectIDs) {
		try {
		    release(oid);
		} catch (NonExistantObjectIDException e) {
		    re = e;
		}
	    }

	    // If any of the releases threw an exception, throw it
	    // here.

	    if (re != null) {
		throw re;
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public void atomicUpdate(boolean clear, Map<Long, byte[]> updateMap) {		
		// insert set is ignored in this case as its uneeded detail
		synchronized(dataSpace){	
			dataSpace.putAll(updateMap);										
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public Long lookup(String name) {		
		synchronized (nameSpace) {
			return nameSpace.get(name);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public long getAppID() {
		return appID;
	}

	/**
	 * NOT IMPLEMENTED.
	 *
	 * {@inheritDoc}
	 */
	public void clear() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * {@inheritDoc}
	 */
	public long create(byte[] data, String name){
		long id = DataSpace.INVALID_ID;
		synchronized(nameSpace){
			if (nameSpace.containsKey(name)){
				return DataSpace.INVALID_ID;
			}
			id = getNextID();
			nameSpace.put(name,id);			
		}
		synchronized(dataSpace){
			dataSpace.put(id,data);
		}
		return id;
	}

	/**
	 * NOT IMPLEMENTED.
	 *
	 * {@inheritDoc}
	 */
	public void close() {
		// TODO Auto-generated method stub
	}

	/**
	 * NOT IMPLEMENTED
	 *
	 * {@inheritDoc}
	 */
	public void destroy(long objectID) {
		// TODO Auto-generated method stub
	}
}
