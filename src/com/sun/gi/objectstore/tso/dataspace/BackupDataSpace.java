/**
 *
 * <p>Title: BackupDataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso.dataspace;

import java.util.Map;
import java.util.Set;

/**
 *
 * <p>Title: BackupDataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface BackupDataSpace {

	/**
	 * 
	 */
	void clear();

	

	/**
	 * @param appID
	 * @param objectID
	 * @return
	 */
	byte[] getObjBytes(long appID, long objectID);



	/**
	 * @param appID
	 * @param clear
	 * @param newNames
	 * @param deleteSet
	 * @param updateMap
	 */
	void atomicUpdate(long appID, boolean clear, Map<String, Long> newNames, Set<Long> deleteSet, Map<Long, byte[]> updateMap);

}
