/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.impl.service.data.store.cache;

/** A cache entry for an object. */
final class ObjectCacheEntry extends BasicCacheEntry<Long, byte[]> {

    /**
     * Creates an object cache entry with the specified object ID and state.
     *
     * @param	oid the object ID
     * @param	state the state
     */
    private ObjectCacheEntry(long oid, State state) {
	super(oid, state);
    }

    /**
     * Creates a cache entry for an created object newly created by a
     * transaction.
     *
     * @param	oid the object ID
     * @param	contextId the context ID associated with the transaction
     * @return	the cache entry
     */
    static ObjectCacheEntry createNew(long oid, long contextId) {
	ObjectCacheEntry entry = new ObjectCacheEntry(oid, State.CACHED_WRITE);
	entry.noteAccess(contextId);
	return entry;
    }

    /**
     * Creates a cache entry for an object that is being fetched from the
     * server.
     *
     * @param	oid the object ID
     * @param	forUpdate whether the object is being fetched for update
     * @return	the cache entry
     */
    static ObjectCacheEntry createFetching(long oid, boolean forUpdate) {
	return new ObjectCacheEntry(
	    oid, forUpdate ? State.FETCHING_WRITE : State.FETCHING_READ);
    }

    /**
     * Creates a cache entry for an object that has been fetched for read from
     * the server on behalf of a transaction.
     *
     * @param	oid the object ID
     * @param	data the object data
     * @param	contextId the context ID associated with the transaction
     * @return	the cache entry     
     */
    static ObjectCacheEntry createCached(
	long oid, byte[] data, long contextId)
    {
	ObjectCacheEntry entry = new ObjectCacheEntry(oid, State.CACHED_READ);
	entry.setValue(data);
	entry.noteAccess(contextId);
	return entry;
    }
}
