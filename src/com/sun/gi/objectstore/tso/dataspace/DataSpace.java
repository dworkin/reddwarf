/*
 * <p>Title: DataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2006 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso.dataspace;

import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.NonExistantObjectIDException;

/**
 * <p>Title: DataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface DataSpace {

    static final long INVALID_ID = Long.MIN_VALUE;

    /**
     * Allocates and returns a new, unused object identifier.  <p>
     *
     * The ID is guaranteed to be unused and unique for the life of
     * this DataSpace.  It is not guaranteed to have any fixed
     * relationship to any other object identifiers previously
     * returned by this method.  <p>
     *
     * @return a newly allocated object identifier
     */
    long getNextID();

    /**
     * Returns a copy of the object as a <code>byte</code> array
     * (which typically represents a serialized object) bound to the
     * given objectID.  <p>
     *
     * @param objectID the identifier of the object to get
     *
     * @return a <code>byte</code> array representing the object, or
     * <code>null</code> if no such object exists
     *
     * (seems inconsistent to return null here and throw
     * NonExistantObjectIDException in other situations -DJE)
     */
    byte[] getObjBytes(long objectID);

    /**
     * Blocks until the object with the given <em>objectID</em> is
     * available, locks the object, and returns.
     *
     * @param objectID the identifier of the object to lock
     *
     * @throws NonExistantObjectIDException if no object with the
     * given <em>objectID</em> exists
     */
    void lock(long objectID) throws NonExistantObjectIDException;

    /**
     * Releases the lock on the object with the given
     * <em>objectID</em>.  <p>
     *
     * Note that any thread may release any lock.  <p>
     *
     * Also note that it is not an error to release the lock on an
     * object that is not currently locked, or to attempt to release
     * the lock on an object that does not exist.  In either of these
     * cases, releasing the lock has no effect on the state of the
     * DataSpace.
     *
     * @param objectID the object whose lock to release
     */
    void release(long objectID);

    /**
     * Atomically updates the DataSpace.  <p>
     *
     * The <code>updateMap</code> contains new bindings between object
     * identifiers and the byte arrays that represent their values. 
     * The <code>insertSet</code> must contain the objectID for each
     * element in the <code>updateMap</code> that represents a
     * <em>new</em> object (a binding for a previously unbound object
     * identifier).  This is important because implementations may
     * handle insertions of new objects differently than updates of
     * existing objects.
     *
     * @param clear <b>NOT USED IN CURRENT IMPL</b>
     *
     * @param newNames new bindings between names and object
     * identifiers
     *
     * @param deleteSet the set of identifiers of objects to delete
     *
     * @param updateMap new bindings between object identifiers and
     * byte arrays
     *
     * @param insertSet new object identifiers
     *
     * @throws DataSpaceClosedException
     *
     * (What is <code>clear</code> supposed to do?  Or is this now
     * unused and should be removed?  -DJE)
     */
    void atomicUpdate(boolean clear,
		    Map<String, Long> newNames, Set<Long> deleteSet,
		    Map<Long, byte[]> updateMap, Set<Long> insertSet)
	    throws DataSpaceClosedException;

    /**
     * Returns the object identifier of the object with a given name
     * (assigned via an {@link #atomicUpdate atomicUpdate}).  <p>
     *
     * If more than one object identifier is bound to the same name,
     * the identifier returned may be chosen arbitrarily from the set
     * of such identifiers.
     *
     * @param name the name of the object
     *
     * @return the object identifier of the object with the given name
     */
    Long lookup(String name);

    /**
     * Return the application identifier for the application that owns
     * this dataspace.
     *
     * @return the application identifier for the application that
     * owns this dataspace
     */
    long getAppID();

    /**
     * Deletes the contents of the DataSpace and releases any locks
     * held on those contents.
     */
    void clear();

    /**
     * Closes the DataSpace, preventing further updates.  <p>
     *
     * All other operations (including allocation of new objects via
     * {@link #getNextID() getNextID} are permitted when the DataSpace
     * is closed.  <p>
     *
     * (Do we want to prevent getNextID when the DataSpace is closed? 
     * Do we want to prevent all operations?  Should there be a way to
     * reopen a DataSpace that has been closed short of creating a new
     * DataSpace with the same appID?  -DJE)
     */
    void close();

	/**
	 * @param name
	 * @return
	 */
	boolean newName(String name);

}