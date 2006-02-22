/**
 *
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
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getNextID()
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getNextID()
	 */
	public long getNextID() {
		synchronized (idMutex) {
			return id++;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getObjBytes(long,
	 *      long)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getObjBytes(long,
	 *      long)
	 */
	public byte[] getObjBytes(long objectID) {		
		synchronized (dataSpace) {
			return dataSpace.get(new Long(objectID));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lock(long)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lock(long)
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#release(long)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#release(long)
	 */
	public void release(long objectID) {		
		synchronized (lockSet) {
			lockSet.remove(new Long(objectID));
			lockSet.notifyAll();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#atomicUpdate(long,
	 *      boolean, java.util.Map, java.util.Set, java.util.Map)
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#atomicUpdate(long,
	 *      boolean, java.util.Map, java.util.Set, java.util.Map)
	 */
	public void atomicUpdate(boolean clear, Map<Long, byte[]> updateMap) {		
		// insert set is ignored in this case as its uneeded detail
		synchronized(dataSpace){	
			dataSpace.putAll(updateMap);										
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lookup(java.lang.String)
	 */
	public Long lookup(String name) {		
		synchronized (nameSpace) {
			return nameSpace.get(name);
		}
	}



	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getAppID()
	 */
	public long getAppID() {
		return appID;
	}



	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#clear()
	 */
	public void clear() {
		// TODO Auto-generated method stub
		
	}

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

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#close()
	 */
	public void close() {
		// TODO Auto-generated method stub
		
	}



	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#destroy(long)
	 */
	public void destroy(long objectID) {
		// TODO Auto-generated method stub
		
	}





}
