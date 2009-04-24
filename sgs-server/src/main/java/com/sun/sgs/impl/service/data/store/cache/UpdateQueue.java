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

/** The interface that nodes use to send updates to the data server. */
public interface UpdateQueue {

    /**
     * Commits changes to the server.  The {@code oids} parameter contains the
     * object IDs of the objects that have been changed.  For each element of
     * that array, the element of the {@code oidValues} array in the same
     * position contains the new value for the object ID, or {@code null} if
     * the object should be removed.  The {@code names} parameter contains the
     * names whose bindings have been changed.  For each element of that array,
     * the element of the {@code nameValues} array in the same position
     * contains the new value for the name binding, or {@code -1} if the name
     * binding should be removed.
     *
     * @param	oids the object IDs
     * @param	oidValues the associated data values
     * @param	names the names
     * @param	nameValues the associated name bindings
     * @throws	IllegalArgumentException if {@code oids} and {@code oidValues}
     *		are not the same length, if {@code oids} contains a negative
     *		value, if {@code names} and {@code nameValues} are not the same
     *		length, or if {@code nameValues} contains a negative value
     */
    void commit(
	long[] oids, byte[][] oidValues, String[] names, long[] nameValues);

    /**
     * A object to be notified of the completion of an operation.
     *
     * @param	<T> the type of the result of the operation
     */
    public interface CompletionHandler<T> {

	/**
	 * Provides notification that the operation has been completed.
	 *
	 * @param	result the result of the operation
	 */
	void completed(T result);
    }

    /**
     * Evicts an object from the cache.  The {@link
     * CompletionHandler#completed} method of {@code handler} will be called
     * with {@code oid} when the eviction has been completed.
     *
     * @param	oid the ID of the object to evict
     * @param	handler the handler to notify when the eviction has been
     *		completed 
     * @throws	IllegalArgumentException if {@code oid} is negative
     */
    void evictObject(long oid, CompletionHandler<Long> handler);

    /**
     * Downgrades access to an object in the cache from write access to read
     * access.  The {@link CompletionHandler#completed} method of {@code
     * handler} will be called with {@code oid} when the downgrade has been
     * completed.
     *
     * @param	oid the object ID to evict
     * @param	handler the handler to notify when the eviction has been
     *		completed 
     */
    void downgradeObject(long oid, CompletionHandler<Long> handler);

    /**
     * Evicts a name binding from the cache.  The {@link
     * CompletionHandler#completed} method of {@code handler} will be called
     * with {@code name} when the eviction has been completed.
     *
     * @param	name the name
     * @param	handler the handler to notify when the eviction has been
     *		completed 
     */
    void evictBinding(String name, CompletionHandler<String> handler);

    /**
     * Downgrades access to a name binding in the cache from write access to
     * read access.  The {@link CompletionHandler#completed} method of {@code
     * handler} will be called with {@code name} when the downgrade has been
     * completed.
     *
     * @param	name the name
     * @param	handler the handler to notify when the downgrade has been
     *		completed 
     */
    void downgradeBinding(String name, CompletionHandler<String> handler);
}
