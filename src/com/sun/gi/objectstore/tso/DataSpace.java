/**
 *
 * <p>Title: DataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso;

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

	/**
	 * 
	 */
	void clearAll();

	/**
	 * @param appID
	 * @param loader
	 * @return
	 */
	DataSpaceTransaction getTransaction(long appID, ClassLoader loader);

	/**
	 * @param dsTrans
	 */
	void returnTransaction(DataSpaceTransaction dsTrans);

}
