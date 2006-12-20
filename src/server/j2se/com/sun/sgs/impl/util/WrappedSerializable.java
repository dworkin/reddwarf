package com.sun.sgs.impl.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * A wrapper for an object that is serializable, but may or may not be
 * a {@link ManagedObject}.
 *
 * @param <T> type of object wrapped
 */
public final class WrappedSerializable<T> implements Serializable {

    /** Indicates whether the object in this wrapper is managed. */
    private boolean managed = false;

    /** The object. */
    private transient Object obj;

    /** The managed reference for the object (non-null if the object is
     * managed).
     */
    private transient ManagedReference ref;

    /**
     * Constructs an instance of this class with the specified object.
     * The specified object must implement {@link Serializable} and may
     * or may not be a {@link ManagedObject}.
     *
     * @param obj an object
     *
     * @throws IllegalArgumentException if the specified object does not
     * implement <code>Serializable</code>
     */
    public WrappedSerializable(T obj) {
	if (obj == null) {
	    throw new NullPointerException("obj is null");
	} else if (!(obj instanceof Serializable)) {
	    throw new IllegalArgumentException("obj not serializable");
	}
	this.obj = obj;
	if (obj instanceof ManagedObject) {
	    managed = true;
	    ref = AppContext.getDataManager().
		createReference((ManagedObject) obj);
	}
    }

    /**
     * Returns the object in this wrapper.
     *
     * @param type the expected class of the object
     * @return T the object in this wrapper
     *
     * @throws ClassCastException if the object in this wrapper is not
     * an instance of the specified type
     */
    public T get(Class<T> type) {
	// TBI: the 'get' could be lazy if this object is managed.
	if (managed) {
	    return ref.get(type);
	} else {
	    return type.cast(obj);
	}
    }

    /* -- Serialization methods -- */

    private void writeObject(ObjectOutputStream out)
	throws IOException
    {
	out.defaultWriteObject();
	if (managed) {
	    out.writeObject(ref);
	} else {
	    out.writeObject(obj);
	}
    }

    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (managed) {
	    ref = (ManagedReference) in.readObject();
	} else {
	    obj = in.readObject();
	}
    }
    
	
}
