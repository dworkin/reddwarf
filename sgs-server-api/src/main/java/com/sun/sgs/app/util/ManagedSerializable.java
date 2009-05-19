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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
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
