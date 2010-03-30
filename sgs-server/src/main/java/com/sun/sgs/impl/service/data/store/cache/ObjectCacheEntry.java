/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * --
 */

package com.sun.sgs.impl.service.data.store.cache;

/**
 * A cache entry for an object.  Only the {@link #key} field may be accessed
 * without holding the associated lock (see {@link Cache#getBindingLock} and
 * {@link Cache#getObjectLock}.  For all other fields and methods, the lock
 * must be held. <p>
 *
 * The value associated with the entry will be {@code null} if the object has
 * been removed from the data store, or {@code NEWLY_CREATED} if the real value
 * isn't known yet because the object is newly created.
 */
final class ObjectCacheEntry extends BasicCacheEntry<Long, byte[]> {

    /**
     * A value to mark entries for newly allocated object IDs which do not yet
     * have the data for the associated object.
     */
    private static final byte[] NEWLY_CREATED = new byte[0];

    /**
     * Creates an object cache entry whose data value is being fetched.
     *
     * @param	oid the object ID
     * @param	contextId the context ID associated with the transaction on
     *		whose behalf the entry was created
     * @param	state the state
     */
    private ObjectCacheEntry(long oid, long contextId, State state) {
	super(oid, contextId, state);
    }

    /**
     * Creates an object cache entry with the specified data, which may be
     * {@code NEWLY_CREATED} for a newly created object, or {@code null} for a
     * removed object.
     *
     * @param	oid the object ID
     * @param	data the object data or {@code null}
     * @param	contextId the context ID associated with the transaction on
     *		whose behalf the entry was created
     * @param	state the state
     */
    private ObjectCacheEntry(
	long oid, byte[] data, long contextId, State state)
    {
	super(oid, data, contextId, state);
    }

    /**
     * Creates a cache entry for an object newly created by a transaction.
     *
     * @param	oid the object ID
     * @param	contextId the context ID associated with the transaction
     * @return	the cache entry
     */
    static ObjectCacheEntry createNew(long oid, long contextId) {
	return new ObjectCacheEntry(
	    oid, NEWLY_CREATED, contextId, State.CACHED_WRITE);
    }

    /**
     * Creates a cache entry for an object that is being fetched from the
     * server on behalf of a transaction.
     *
     * @param	oid the object ID
     * @param	contextId the context ID associated with the transaction
     * @param	forUpdate whether the object is being fetched for update
     * @return	the cache entry
     */
    static ObjectCacheEntry createFetching(
	long oid, long contextId, boolean forUpdate)
    {
	return new ObjectCacheEntry(
	    oid, contextId,
	    forUpdate ? State.FETCHING_WRITE : State.FETCHING_READ);
    }

    /**
     * Creates a cache entry for an object that has been fetched for read from
     * the server on behalf of a transaction without having first created an
     * entry marked as being fetched.
     *
     * @param	oid the object ID
     * @param	data the object data or {@code null} for a removed object
     * @param	contextId the context ID associated with the transaction
     * @return	the cache entry     
     */
    static ObjectCacheEntry createCached(
	long oid, byte[] data, long contextId)
    {
	return new ObjectCacheEntry(oid, data, contextId, State.CACHED_READ);
    }

    @Override
    public String toString() {
	boolean hasValue = hasValue();
	byte[] data = hasValue ? super.getValue() : null;
	return "ObjectCacheEntry[" +
	    "oid:" + key +
	    (!hasValue ? "" :
	     ", data:" + ((data == NEWLY_CREATED) ? "new"
			  : (data == null) ? "null"
			  : "byte[" + data.length + "]")) +
	    ", contextId:" + getContextId() +
	    ", state:" + getState() +
	    "]";
    }

    /**
     * Returns the hash code for this entry's key.
     *
     * @return	the hash code for the key
     */
    int keyHashCode() {
	return keyHashCode(key);
    }

    /**
     * Returns the hash code for an object ID.
     *
     * @param	oid the object ID
     * @return	the hash code for the object ID
     */
    static int keyHashCode(long oid) {
	return (int) (oid ^ (oid >>> 32));
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation checks that entry is not for a newly created that
     * has no data yet.
     */
    @Override
    byte[] getValue() {
	byte[] data = super.getValue();
	if (data == NEWLY_CREATED) {
	    throw new IllegalStateException(
		"Entry value is not valid: " + this);
	}
	return data;
    }

    /**
     * Returns whether the entry represents a newly created object ID which
     * does not yet contain the data for the associated object.
     *
     * @return	whether the entry is for a newly created object
     */
    boolean isNewlyCreated() {
	return super.getValue() == NEWLY_CREATED;
    }
}
