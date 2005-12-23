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
 * Description:
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
	Map<Long, Map<Long, byte[]>> dataSpace = new LinkedHashMap<Long, Map<Long, byte[]>>();

	Map<Long, Map<String, Long>> nameSpace = new LinkedHashMap<Long, Map<String, Long>>();

	Map<Long, Set<Long>> lockSets = new LinkedHashMap<Long, Set<Long>>();

	private Object idMutex = new Object();

	private int id = 1;

	public InMemoryDataSpace() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpace#clearAll()
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#clearAll()
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#clearAll()
	 */
	public void clearAll() {

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
	public byte[] getObjBytes(long appID, long objectID) {
		Map<Long, byte[]> appMap;
		synchronized (dataSpace) {
			appMap = dataSpace.get(new Long(appID));
		}
		if (appMap != null) {
			synchronized (appMap) {
				return appMap.get(new Long(objectID));
			}
		}
		return null;
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
	public void lock(long appID, long objectID)
			throws NonExistantObjectIDException {
		Set<Long> lockSet;
		Map<Long, byte[]> appDataSpace;
		Long id = new Long(appID);

		synchronized (dataSpace) {
			appDataSpace = dataSpace.get(appID);
		}
		if (appDataSpace == null) {
			throw new NonExistantObjectIDException();
		}
		synchronized (appDataSpace) {
			if (!appDataSpace.containsKey(objectID)) {
				throw new NonExistantObjectIDException();
			}
		}
		synchronized (lockSets) {
			lockSet = lockSets.get(id);
			if (lockSet == null) {
				lockSet = new HashSet<Long>();
				lockSets.put(id, lockSet);
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
	public void release(long appID, long objectID) {
		Set<Long> lockSet;
		Long id = new Long(appID);
		synchronized (lockSets) {
			lockSet = lockSets.get(id);
		}
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
	public void atomicUpdate(long appID, boolean clear,
			Map<String, Long> newNames, Set<Long> deleteSet,
			Map<Long, byte[]> updateMap) {	
		
		Long lappID = new Long(appID);
		Map<Long, byte[]> objMap = null;
		Map<String, Long> nameMap = null;
		synchronized (dataSpace) {	
			synchronized (nameSpace) {
				objMap = dataSpace.get(appID);
				nameMap = nameSpace.get(appID);
				if(objMap == null) {				
					objMap = new LinkedHashMap<Long, byte[]>();
					dataSpace.put(lappID, objMap);
				}
				if (nameMap == null) {
					nameMap = new LinkedHashMap<String, Long>();
					nameSpace.put(lappID, nameMap);
				}
			}
		}
		synchronized(objMap){
			synchronized(nameMap) {
				objMap.putAll(updateMap);
				nameMap.putAll(newNames);
				for (Long id : deleteSet) {
					objMap.remove(id);
				}								
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lookup(java.lang.String)
	 */
	public Long lookup(long appID, String name) {
		Map<String, Long> nameMap;
		synchronized (nameSpace) {
			nameMap = nameSpace.get(new Long(appID));
		}
		if (nameMap==null){
			return DataSpace.INVALID_ID;
		}
		synchronized (nameMap) {
			return nameMap.get(name);
		}
	}

}
