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




/**
 *
 * <p>Title: InMemoryDataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class InMemoryDataSpace implements DataSpace  {
	Map<Long,Map<Long,byte[]>> dataSpace = new HashMap<Long,Map<Long,byte[]>>();

	private Object idMutex= new Object();
	private int id = 1;
	
	public InMemoryDataSpace(){
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.DataSpace#clearAll()
	 */
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#clearAll()
	 */
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#clearAll()
	 */
	public void clearAll() {
	
	}

	
		// internal routines to the system, used by transactions
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getNextID()
	 */
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getNextID()
	 */
	public long getNextID() {
		// TODO Auto-generated method stub
		synchronized(idMutex){
			return id ++;
		}
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getObjBytes(long, long)
	 */
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#getObjBytes(long, long)
	 */
	public byte[] getObjBytes(long appID, long objectID) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lock(long)
	 */
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lock(long)
	 */
	public void lock(long objectID) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#release(long)
	 */
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#release(long)
	 */
	public void release(long objectID) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#atomicUpdate(long, boolean, java.util.Map, java.util.Set, java.util.Map)
	 */
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#atomicUpdate(long, boolean, java.util.Map, java.util.Set, java.util.Map)
	 */
	public void atomicUpdate(long appID, boolean clear, Map<String, Long> newNames, Set<Long> deleteSet, Map<Long, byte[]> updateMap) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.dataspace.DataSpace#lookup(java.lang.String)
	 */
	public Long lookup(String name) {
		// TODO Auto-generated method stub
		return null;
	}

}
