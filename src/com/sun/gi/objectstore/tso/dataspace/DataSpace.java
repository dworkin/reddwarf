/**
 *
 * <p>Title: DataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso.dataspace;

import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.NonExistantObjectIDException;

/**
 *
 * <p>Title: DataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface DataSpace {

	static final long INVALID_ID = Long.MIN_VALUE;


	// internal routines to the system, used by transactions
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getNextID()
	 */
	public long getNextID();

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getObjBytes(long, long)
	 */
	public byte[] getObjBytes(long objectID);

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lock(long)
	 */
	public void lock(long objectID) throws NonExistantObjectIDException;

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#release(long)
	 */
	public void release(long objectID);

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#atomicUpdate(long, boolean, java.util.Map, java.util.Set, java.util.Map)
	 */
	public void atomicUpdate(boolean clear,
			Map<String, Long> newNames, Set<Long> deleteSet,
			Map<Long, byte[]> updateMap, Set insertSet);

	/**
	 * @param name
	 * @return
	 */
	public Long lookup(String name);

	/**
	 * @return
	 */
	public long getAppID();

	/**
	 * 
	 */
	public void clear();
	

}