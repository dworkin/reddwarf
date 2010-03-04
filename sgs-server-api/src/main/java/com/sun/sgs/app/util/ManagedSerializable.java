/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.Serializable;

/**
 * A utility class for wrapping a {@link Serializable} object within a {@code
 * ManagedObject} instance.  This class is primarily intended to allow class
 * that does not implement {@code ManagedObject} to be persistently stored and
 * accessed through a {@code ManagedReference}.
 *
 * <p>
 *
 * The serialization costs for a class largely depend on the size of the
 * objects that it references.  The {@code ManagedReference} class allows
 * developers the ability to create breaks in the serialization graphs where
 * not all of the fields are deserialized when a class is deserialized.  The
 * {@code ManagedSerializable} class is intended to be used for wrapping large
 * serializable objects, such as collections, in order to break the
 * serialization graph, thereby reducing the number of bytes read and written.
 * Note that wrapping these types of objects does not guarantee a performance
 * improvement.
 *
 * <p>
 *
 * Following is an example of where an existing class has been retrofitted to
 * have {@code ManagedReference} references to its large fields, rather than
 * standard references.
 *
 * <p>
 *
 * <b>Before:</b>
 *
 * <pre>
 * <code>
 * public class MyPlayerObj {
 *     String name;
 *     Collection< Item > inventory;
 *     MapArea currentLocation;
 *
 *     public MyPlayerObj(...) {
 *         ...
 *         inventory = new ArrayList< Item >();
 *     }
 *
 *     ...
 *
 *     public void findNearbyPlayers() {
 *         for (Player p : currentLocation.getPlayers())
 *             ...
 *     }
 * }
 * </code>
 * </pre>
 *
 * <b>After:</b>
 *
 * <pre>
 * {@code
 * public class MyPlayerObj {
 *     String name;
 *     ManagedReference< ManagedSerializable< Collection< Item >>> inventoryRef;
 *     ManagedReference< ManagedSerializable< MapArea >> currentLocationRef;
 *
 *     public MyPlayerObj(...) {
 *         ...
 *         Collection< Item > inventory = new ArrayList< Item >();
 *         inventoryRef = AppContext.getDataManager().
 *             createReference(
 *                 new ManagedSerializable< Collection< Item>>(inventory));
 *     }
 *
 *     ...
 *
 *     public void findNearbyPlayers() {
 *         ManagedSerializable< MapArea > curLocWrapper =
 *             currentLocationRef.get();
 *         MapArea currentLocation = curLocWrapper.get();
 *
 *         for (Player p : currentLocation.getPlayers())
 *             ...
 *     }
 * }
 * }
 * </pre>
 * 
 * Application developers are responsible for removing {@code
 * ManagedSerializable} instances by calling {@link DataManager#removeObject
 * DataManager.removeObject}.  Developers should call {@link
 * DataManager#markForUpdate DataManager.markForUpdate} or {@link
 * ManagedReference#getForUpdate DataManager.getForUpdate} if the application
 * modifies objects wrapped by instances of this class.
 *
 * @param <T> the type of the wrapped object
 */
public class ManagedSerializable<T> implements ManagedObject, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The serializable object being wrapped by this instance, which may be
     * {@code null}.
     *
     * @serial
     */
    private T object;

    /**
     * Constructs an instance of this class that wraps the specified object,
     * which must not implement {@link ManagedObject}, but must either
     * implement {@link Serializable} or be {@code null}.
     *
     * @param object the object to wrap
     *
     * @throws IllegalArgumentException if {@code object} is an instance of
     *         {@code ManagedObject}, or if {@code object} is not {@code null}
     *         and does not implement {@code Serializable}
     */
    public ManagedSerializable(T object) {
	if (object instanceof ManagedObject) {
	    throw new IllegalArgumentException(
		"The argument must not implement ManagedObject");
	} else if (object != null && !(object instanceof Serializable)) {
	    throw new IllegalArgumentException(
		"The argument must implement Serializable");
	}
	this.object = object;
    }

    /**
     * Returns {@code true} if {@code o} is a {@code ManagedSerializable} that
     * wraps an object that is equal to the object wrapped by this instance.
     *
     * @param o the object to compared for equality with this instance
     *
     * @return {@code true} if {@code o} is a {@code ManagedSerializable} that
     *         wraps an object equal to the object wrapped by this instance
     */
    @Override
    public boolean equals(Object o) {
	if (o instanceof ManagedSerializable) {
	    ManagedSerializable m = (ManagedSerializable) o;
	    return (object == null)
		? object == m.object
		: object.equals(m.object);
	}
	return false;
    }

    /**
     * Returns the object wrapped by this instance, which may be {@code null}.
     *
     * @return the object wrapped by this instance
     */
    public T get() {
	return object;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
	return (object == null) ? 0 : object.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
	return getClass().getName() +
	    "[" + (object == null ? "null" : object.toString()) + "]";
    }

    /**
     * Replaces the object wrapped by this instance with the specified object,
     * which must not implement {@link ManagedObject}, but must either
     * implement {@link Serializable} or be {@code null}.
     *
     * @param object the new object to wrap
     *
     * @throws IllegalArgumentException if {@code object} is an instance of
     *         {@code ManagedObject}, or if {@code object} is not {@code null}
     *         and does not implement {@code Serializable}
     */
    public void set(T object) {
	if (object instanceof ManagedObject) {
	    throw new IllegalArgumentException(
		"The argument must not implement ManagedObject");
	} else if (object != null && !(object instanceof Serializable)) {
	    throw new IllegalArgumentException(
		"The argument must implement Serializable");
	}
	this.object = object;
	AppContext.getDataManager().markForUpdate(this);
    }
}
