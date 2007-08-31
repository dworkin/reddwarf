/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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
 * the field that it references.  The {@code ManagedReference} class
 * allows developers the ability to create breaks in the serialization
 * graphs where not all of the fields are deserized when a class is
 * deserialzied.  The {@code ManagedSerializable} class is intended to
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
 *     public findNearbyPlayers() {
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
 *     public findNearbyPlayers() {
 *         ManagedSerializable&lt;MapArea&gt; mapAreaWrapper = 
 *             mapAreaRef.get(ManagerSerializable.class);
 *         MapArea currentLocation = mapAreaWrapper.get();
 *
 *         for (Player p : currentLocation.getPlayers()) 
 *             ....
 *     }
 * }
 * </pre>
 *     
 * Application developers are still responsible for removing a {@code
 * ManagedSerializable} instance from the {@code DataManager}.  Any
 * changes to state of the object that is wrapped require that
 * developer notify the {@code DataManager} that this instance should
 * be marked for update.
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
     * Constructs a managed wrapper around this object
     *
     * @param object the object to be stored in the datastore
     */
    public ManagedSerializable(T object) {
	this.object = object;
	AppContext.getDataManager().markForUpdate(this);
    }

    /**
     * Returns {@code true} if {@code o} is a {@code
     * ManagedSerializable} and the objects contained within are both
     * equal.
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
     */
    public void set(T object) {
	this.object = object;
	AppContext.getDataManager().markForUpdate(this);
    }

}
