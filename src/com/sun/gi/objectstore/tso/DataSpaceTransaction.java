/**
 *
 * <p>Title: DataSpaceTransaction.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso;

import java.io.Serializable;

import com.sun.gi.objectstore.tso.TSOTransaction.DataHeader;

/**
 *
 * <p>Title: DataSpaceTransaction.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface DataSpaceTransaction {

	/**
	 * @param object
	 * @return
	 */
	long create(Serializable object);

	/**
	 * @param objectID
	 */
	void destroy(long objectID);

	/**
	 * @param objectID
	 * @return
	 */
	Serializable read(long objectID);

	/**
	 * @param name
	 * @param tsID
	 */
	void registerName(String name, long tsID);

	/**
	 * @param objectID
	 * @return
	 */
	void lock(long objectID);

	/**
	 * @param objectID
	 */
	void release(long objectID);

	/**
	 * @param objectID
	 * @param dh
	 */
	void write(long objectID, DataHeader dh);

	/**
	 * 
	 */
	void clear();

	/**
	 * 
	 */
	void commit();

	/**
	 * 
	 */
	void abort();

	/**
	 * @param name
	 * @return
	 */
	long lookupName(String name);

	
	
}
