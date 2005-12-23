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

import com.sun.gi.objectstore.NonExistantObjectIDException;

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
	 * @throws NonExistantObjectIDException 
	 */
	Serializable read(long objectID) throws NonExistantObjectIDException;

	/**
	 * @param name
	 * @param tsID
	 */
	void registerName(String name, long tsID);

	/**
	 * @param objectID
	 * @return
	 * @throws NonExistantObjectIDException 
	 */
	void lock(long objectID) throws NonExistantObjectIDException;

	/**
	 * @param objectID
	 */
	void release(long objectID);

	/**
	 * @param objectID
	 * @param dh
	 */
	void write(long objectID, Serializable obj);

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

	/**
	 * @param appID
	 */
	void clear(long appID);

	
	
}
