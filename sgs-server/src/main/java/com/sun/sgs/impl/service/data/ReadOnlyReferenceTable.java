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

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * Stores the shared state of all {@link ReadOnlyReference} instances created
 * within a particular transaction.  This class is logically part of the {@code
 * ReadOnlyReference} class.
 *
 * <p>
 *
 * When {@code ReadOnlyReference} is first created, the reference is registered
 * with the table using the information with which it was created.  This
 * ensures that any new references created based on the same oid or {@code
 * ManagedObject} will use the same {@code ReadOnlyReference} instance.
 *
 * <p>
 *
 * If no reference had ever been created for a {@code ManagedObject}, the the
 * reference will registered using the {@link
 * #put(ManagedObject,ReadOnlyReference)}.  Otherwise, if a {@code
 * ReadOnlyReference} is being created based on an oid, then it will be
 * registered using {@link #put(Long,ReadOnlyReference)}.  For the later type
 * of references, the object that they refer to will not have been loaded yet,
 * and so the {@code cachedObjects} map will not contain a mapping to those
 * references.  When the object is loaded, however, then the mapping will be
 * added.
 *
 * @see Context
 * @see ReadOnlyReference
 * @see ReadOnlyReference#getReference(Context,Object)
 * @see ReadOnlyReference#getOrCreateIfNotPresent(Context,Object)
 * @see ReadOnlyReference#getOrCreateIfNotPresent(Context,long)
 */
final class ReadOnlyReferenceTable {

    /**
     * A mapping from a {@code ManagedObject} instance to the {@code
     * ReadOnlyReference} that refers to it.  This mapping contains all the new
     * {@code ReadOnlyReference} instances created during the context of the
     * current transaction.
     *
     * @see ReadOnlyReference#getOrCreateIfNotPresent(Context,Object)
     */
    private final Map<ManagedObject,ReadOnlyReference<?>> cachedObjects;

    /**
     * A mapping from oid to the {@code ReadOnlyReference} used for that id
     * within the current transaction context.
     */
    private final Map<Long,ReadOnlyReference<?>> oidToRef;
    
    
    ReadOnlyReferenceTable() { 
	cachedObjects = 
	    new IdentityHashMap<ManagedObject,ReadOnlyReference<?>>();
	oidToRef = new HashMap<Long,ReadOnlyReference<?>>();
    }
    

    void put(ManagedObject object, ReadOnlyReference<?> ref) {
	cachedObjects.put(object, ref);
	oidToRef.put(ref.oid, ref);
    }
    
    void put(Long oid, ReadOnlyReference<?> ref) {
	oidToRef.put(oid, ref);
    }

    ReadOnlyReference<?> get(ManagedObject object) {
	return cachedObjects.get(object);
    }

    ReadOnlyReference<?> get(Long oid) {
	return oidToRef.get(oid);
    }

}