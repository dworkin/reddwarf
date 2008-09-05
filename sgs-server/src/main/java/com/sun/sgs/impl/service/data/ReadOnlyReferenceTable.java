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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

import java.util.logging.Logger;

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
 * @see ReadOnlyDataCache
 */
final class ReadOnlyReferenceTable {

    /**
     * The logger for reporting modifications to read-only data if modification
     * detection is enabled.
     *
     * @see #modificationCheckingEnabled
     */
    private static final Logger readOnlyModificationDetection = 
	Logger.getLogger(ReadOnlyDataCache.class.getName() +
			 ".object.modifications");

    /**
     * A class variable for determining whether all instances of this class
     * will have modification checking enabled.  This value is currently set by
     * the {@link ReadOnlyDataCache} upon its construction based on the
     * specified system properties.
     *
     * @see ReadOnlyDataCache
     */
    static boolean modificationCheckingEnabled = false;

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

    
    private final Map<Long,byte[]> serializedFormOfCachedObjects;
    
    
    ReadOnlyReferenceTable() { 
	cachedObjects = 
	    new IdentityHashMap<ManagedObject,ReadOnlyReference<?>>();
	oidToRef = new HashMap<Long,ReadOnlyReference<?>>();

	serializedFormOfCachedObjects = 
	    (modificationCheckingEnabled) ? new HashMap<Long,byte[]>() : null;
    }
    

    /**
     *
     *
     * @see ReadOnlyReference#getOrCreateIfNotPresent(Context,Object)
     * @see ReadOnlyReference#setObject(ManagedObject)
     */
    void put(ManagedObject object, ReadOnlyReference<?> ref) {
	cachedObjects.put(object, ref);
	oidToRef.put(ref.oid, ref);

	// If modification checking is enabled, store the serialized form of
	// this object.  Caching the serialized form at this point relies on
	// the fact that ReadOnlyReference will always call put() before
	// returning the object to the application.  This ensures that a
	// canonical copy can be serialized before any modifications could take
	// place.
	if (serializedFormOfCachedObjects != null) {
	    serializedFormOfCachedObjects.put(ref.getOid(), serialize(object));
	}
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

    /**
     * Returns the serialized form of the provided object.
     *
     * @param o the object to serialize
     *
     * @return the serialized form of the object
     */
    private static byte[] serialize(Object o) {
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	try {
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(o);
	    oos.close();
	} catch (IOException ioe) {
	    // figure out what to do in the case
	}
	return baos.toByteArray();
    }
    
    /**
     * If modification detection is enabled, checks that all the objects
     * accessed during the current transaction have not been modified.  If
     * modification detection is disabled, the method does nothing.
     *
     * @see #modificationDetectionEnabled
     */
    void validate() {
	if (serializedFormOfCachedObjects != null) {
	    for (Map.Entry<ManagedObject,ReadOnlyReference<?>> e : 
		     cachedObjects.entrySet()) {
		ReadOnlyReference<?> ref = e.getValue();
		byte[] unmodified = 
		    serializedFormOfCachedObjects.get(ref.getOid());
		ManagedObject accessedObj = e.getKey();
		if (!Arrays.equals(unmodified, serialize(accessedObj))) {
		    readOnlyModificationDetection.warning(
			"Read-only object modified! id: " + ref.oid + 
			" value: " + accessedObj);
		}
	    }
	}
    }
}