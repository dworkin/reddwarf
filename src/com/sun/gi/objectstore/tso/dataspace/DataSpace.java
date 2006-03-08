/*
 * <p>Title: DataSpace.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2006 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso.dataspace;

import java.util.List;
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
     * NonExistantObjectIDException in other situations -DJE) (agreed. 
     * There are also some functions that return Long and others that
     * return long.  The ones that return Long return null for error
     * and the ones that return long return DataSpace.INVALID ID. 
     * This should probably all get sorted out post GDC...  JK)
     */
    byte[] getObjBytes(long objectID);

    /**
     * Blocks until the object with the given <em>objectID</em> is
     * available, locks the object, and returns.
     * 
     * Note that this is not a counting lock, calling lock twice on
     * the same ID is a deadlock situation as you will sit and wait
     * for yourself to free the lock.
     * 
     * Also note that there is no notion of a lock owner.  Anyone who
     * knows the number can free a lock by calling release on it. 
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
     * Note that any thread may release any lock.  (see above.)<p>
     *
     * Also note that it is not an error to release the lock on an
     * object that is not currently locked, or to attempt to release
     * the lock on an object that does not exist.  In either of these
     * cases, releasing the lock has no effect on the state of the
     * DataSpace.
     *
     * @param objectID the object whose lock to release
     *
     * @throws NonExistantObjectIDException if no object with the
     * given <em>objectID</em> exists
     */
    void release(long objectID) throws NonExistantObjectIDException;

    /**
     * Releases all of the locks in the given set of object IDs.  <p>
     *
     * Note that any thread may release any lock.  (see above.)<p>
     *
     * Also note that it is not an error to release the lock on an
     * object that is not currently locked, or to attempt to release
     * the lock on an object that does not exist.  In either of these
     * cases, releasing the lock has no effect on the state of the
     * DataSpace.  A {@link NonExistantObjectIDException} is thrown if
     * any of the objects do not exist, but this does not prevent all
     * of the other objects from being released.
     *
     * @param objectIDs a set of IDs for objects whose locks to
     * release
     *
     * @throws NonExistantObjectIDException if one or more of the
     * object IDs does not exist
     */
    void release(Set<Long> objectIDs) throws NonExistantObjectIDException;

    /**
     * Atomically updates the DataSpace.  <p>
     *
     * The <code>updateMap</code> contains new bindings between object
     * identifiers and the byte arrays that represent their values. 
     *
     * @param clear <b>NOT USED IN CURRENT IMPL</b>
     *
     * @param updateMap new bindings between object identifiers and
     * byte arrays
     *
     * @throws DataSpaceClosedException
     *
     * (What is <code>clear</code> supposed to do?  Or is this now
     * unused and should be removed?  -DJE)
     */
    void atomicUpdate(boolean clear, Map<Long, byte[]> updateMap,
	    List<Long> deleted)
	    throws DataSpaceClosedException;

    /**
     * Returns the object identifier of the object with a given name
     * (assigned via a {@link #create create}).  <p>
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
     * 
     * Clear is an immediate (non-transactional) chnage to the
     * DataSpace
     */
    void clear();
    
    /**
     * Creates a new element in the DataSpace.  <p>
     *
     * If <code>name</code> is non-<code>null</code> and
     * <code>name</code> is already in the DataSpace then this method
     * will fail.  <p>
     * 
     * Create is an immediate (non-transactional) change to the
     * DataSpace.
     * 
     * @return a new objectID if successful, or
     * <code>DataSpace.INVALID_ID</code> if it fails
     */
    public long create(byte[] data, String name);

    /**
     * Closes the DataSpace, preventing further updates.  <p>
     *
     * All other operations are permitted when the DataSpace is
     * closed.  <p>
     */
    void close();

    /**
     * Destroys the object associated with objectID and removes the
     * name associated with that ID (if any).  <p>
     * 
     * destroy is an immediate (non-transactional) change to the
     * DataSpace.
     * 
     * @param The objectID of the object to destroy
     *
     * @throws NonExistantObjectIDException if no object with the
     * given <em>objectID</em> exists
     */
    // void destroy(long objectID) throws NonExistantObjectIDException;
}