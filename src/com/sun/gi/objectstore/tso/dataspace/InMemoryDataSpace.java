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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.tso.DataSpace;
import com.sun.gi.objectstore.tso.DataSpaceTransaction;

/**
 *
 * <p>Title: InMemoryDataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class InMemoryDataSpace implements DataSpace {
	Map<Long,Map<Long,byte[]>> dataSpace = new HashMap<Long,Map<Long,byte[]>>();
	BackupDataSpace backup;
	private Object idMutex= new Object();
	private int id = 1;
	
	public InMemoryDataSpace(BackupDataSpace backupSpace){
		backup = backupSpace;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.DataSpace#clearAll()
	 */
	public void clearAll() {
		dataSpace.clear();
		backup.clear();
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.DataSpace#getTransaction(long, java.lang.ClassLoader)
	 */
	public DataSpaceTransaction getTransaction(long appID, ClassLoader loader) {		
		return new InMemoryDataSpaceTransaction(this,appID,loader,backup);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.DataSpace#returnTransaction(com.sun.gi.objectstore.tso.DataSpaceTransaction)
	 */
	public void returnTransaction(DataSpaceTransaction dsTrans) {
		// nothing to do yet
		
	}

		// internal routines to the system, used by transactions
	/**
	 * @return
	 */
	long getNextID() {
		// TODO Auto-generated method stub
		synchronized(idMutex){
			return id ++;
		}
	}

	/**
	 * @param appID
	 * @param objectID
	 * @return
	 */
	byte[] getObjBytes(long appID, long objectID) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param objectID
	 */
	void lock(long objectID) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param objectID
	 */
	void release(long objectID) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param appID 
	 * @param clear
	 * @param newNames
	 * @param deleteSet
	 * @param updateMap
	 */
	public void atomicUpdate(long appID, boolean clear, Map<String, Long> newNames, Set<Long> deleteSet, Map<Long, byte[]> updateMap) {
		// TODO Auto-generated method stub
		
	}

}
