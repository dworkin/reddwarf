/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;

/**
 * A service-level facility for keeping peristed objects cached in memory
 * rather than in the backing data store.  All implementations should support
 * the ability to control the cache size through the following property:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>{@value #CACHE_SIZE_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> defined by the implementation
 * </dl> 
 *
 * <p>
 *
 * Implementations are free to define the types of objects kept in memory, and
 * any modification checking behavior.
 */
interface DataCache {

    /**
     * The property used by implementations of this class to set the number of
     * elements held in the cache
     */
    public static final String CACHE_SIZE_PROPERTY =
	DataCache.class.getName() + "cache.size";

    /**
     * Caches the object with the provide id.
     *
     * @param oid the id of the object
     * @param o the object being cached
     */
    public void cacheObject(Long oid, ManagedObject o);

    /**
     * Returns {@code true} if the object with the provided id is currently
     * within this cache
     *
     * @param oid the id of the object being sought
     *
     * @return {@code true} if the object is current in the cache
     */
    public boolean contains(Long oid);

    /**
     * Returns the object associated with the provided id or {@code null} if no
     * object with the provided id is in the cache.  Users can check that
     * {@link #contains(Long)} returns {@code false} to determine if the 
     * object was actually in the cache but its value was {@code null}.
     *
     * @param oid the id of the object being sought
     *
     * @return the object associated with the provided oid or {@code null} if
     *         the id is not in the cachce
     */
    public ManagedObject lookup(Long oid);

}