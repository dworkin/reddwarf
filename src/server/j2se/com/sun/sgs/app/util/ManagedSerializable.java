/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;

import java.io.Serializable;


/**
 * A utility class for wrapping {@code Serializable} objects within a
 * {@code ManagedObject} instance.  This class is primarily intended
 * to allow class that do not implement {@code ManagedObject} to be
 * persistently stored and accessed through a {@code
 * ManagedReference}.
 *
 * <p>
 *
 * The serialization costs for a class largely depend on the size of
 * the objects that it references.  The {@code ManagedReference} class
 * allows developers the ability to create breaks in the serialization
 * graphs where not all of the fields are deserialized when a class is
 * deserialized.  The {@code ManagedSerializable} class is intended to
 * be used for wrapping large serializable objects, such as
 * collections, in order to break the serialization graph, thereby
 * reducing the number of bytes read and written.  Note that wrapping
 * these types of objects does not guarantee a performance
 * improvement.
 *
 * <p>
 * 
 * Following is an example of where an existing class has been
 * retrofitted to have {@code ManagedReference} references to its
 * large fields, rather than Java references.
 *
 * <p>
 * <b>Sample Conversion</b> (note: the following classes are made-up)
 * <br> <i>before</i>: <br>
 * <pre>
 * public class MyPlayerObj {
 *     String name;
 *     Collection&lt;Item&gt; inventory;
 *     MapArea currentLocation;
 *     
 *     public MyPlayerObj(...) { 
 *         ... 
 *         inventory = new ArrayList&lt;Item&gt;();
 *     }
 *
 *     ...
 *
 *     public void findNearbyPlayers() {
 *         for (Player p : currentLocation.getPlayers()) 
 *             ....
 *     }
 * }
 * </pre>
 *
 * <i>after</i>:<br>
 * <pre>
 * public class MyPlayerObj {
 *     String name;
 *     
 *     // a reference of type ManagedSerializable&lt;Collection&lt;Item&gt;&gt;
 *     ManagedReference inventoryRef;
 *
 *     // a reference of type ManagedSerializable&lt;MapArea&gt;
 *     ManagedReference currentLocationRef;
 *     
 *     public MyPlayerObj(...) { 
 *         ... 
 *         Collection&lt;Item&gt; inventory = new ArrayList&lt;Item&gt;();
 *         inventoryRef = AppContext.getDataManager().
 *             createReference(
 *                 new ManagedSerializable&lt;Collection&lt;Item&gt;&gt;(inventory));
 *     }
 *
 *     ...
 *
 *     public void findNearbyPlayers() {
 *         ManagedSerializable&lt;MapArea&gt; curLocWrapper = 
 *             currentLocationRef.get(ManagerSerializable.class);
 *         MapArea currentLocation = curLocWrapper.get();
 *
 *         for (Player p : currentLocation.getPlayers()) 
 *             ...
 *     }
 * }
 * </pre>
 *     
 * Application developers are still responsible for removing a {@code
 * ManagedSerializable} instance from the {@code DataManager}.
 * Developers are strongly encouraged to notify the {@code
 * DataManager} that this instance should be marked for update when
 * any changed occur to the value contained by this instance. 
 *
 * @see com.sun.sgs.app.ManagedReference
 * @see com.sun.sgs.app.DataManager
 * @see com.sun.sgs.app.DataManager#removeObject(ManagedObject)
 */
public class ManagedSerializable<T> implements ManagedObject, Serializable {
	
    private static final long serialVersionUID = 1;
    
    /**
     * The serializable object being wrapped by this instance
     */
    private T object;
    
    /**
     * Constructs a managed wrapper around this object.  {@code
     * ManagedObject} instances are not permitted as this class is
     * already an instance of {@code ManagedObject} and may therefor
     * not have a Java reference to another instance of {@code
     * ManagedObject}.
     *
     * @param object the object to be stored in the datastore
     *
     * @throws IllegalArgumentException if {@code object} is an
     *         instance of {@code ManagedObject}
     */
    public ManagedSerializable(T object) {
	if (object instanceof ManagedObject) 
	    throw new IllegalArgumentException("cannot create a " +
					       "ManagerSerializable for a " +
					       "ManagedObject");      
	this.object = object;
	AppContext.getDataManager().markForUpdate(this);
    }

    /**
     * Returns {@code true} if {@code o} is a {@code
     * ManagedSerializable} and the objects contained within are both
     * equal.
     *
     * @param o the object to compared for equality with this instance
     *
     * @return {@code true} if {@code o} is a {@code
     *         ManagedSerializable} and the objects contained within
     *         are both equal.
     */
    public boolean equals(Object o) {
	if (o instanceof ManagedSerializable) {
	    ManagedSerializable m = (ManagedSerializable)o;
	    return (object == null) 
		? object == m.object
		: object.equals(m.object);
	}
	return false;
    }

    /**
     * Returns the object wrapped by this instance.
     *
     * @return the object
     */
    public T get() {
	return object;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
	return (object == null) ? 0 : object.hashCode();
    }

    /**
     * Replaces the previous object contained by this instance with
     * the provided object.
     *
     * @param object the new object to replace the previously wrapped
     *        object
     *
     * @throws IllegalArgumentException if {@code object} is an
     *         instance of {@code ManagedObject}
     */
    public void set(T object) {
	if (object instanceof ManagedObject) 
	    throw new IllegalArgumentException("cannot set the value of a " +
					       "ManagerSerializable to be a " +
					       "ManagedObject");      
	this.object = object;
	AppContext.getDataManager().markForUpdate(this);
    }

}
