/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.impl.sharedutil.Objects;
import java.io.Serializable;

/**
 * A wrapper for an object that is serializable, but may or may not be
 * a {@link ManagedObject}.  An instance of this serializable wrapper
 * contains a managed reference to the {@code object} specified during
 * construction as follows:
 * <p> <ul>
 *
 * <li>If the {@code object} implements {@code ManagedObject}, the
 * {@code WrappedSerializable} instance contains a {@link
 * ManagedReference} directly to that object. </li>
 
 * <li> Otherwise, if the {@code object} implements {@link
 * Serializable} but does not implement {@code ManagedObject}, the
 * {@code WrappedSerializable} instance wraps the {@code object} in a
 * {@code ManagedObject} and contains a reference to the
 * wrapper. </li>
 * </ul>
 * <p>
 * When an instance of {@code WrappedSerializable} is no longer in
 * use, the {@link #remove remove} method should be invoked on the
 * instance so that if a wrapper was created for the {@code object},
 * the wrapper can be removed.
 *
 * @param <T> type of object wrapped
 */
public final class WrappedSerializable<T> implements Serializable {

    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The managed reference for the object or wrapper.
     */
    private ManagedReference<?> ref = null;

    /**
     * Constructs an instance of this class with the specified object.
     * The specified object must implement {@link Serializable} and may
     * or may not be a {@link ManagedObject}.
     *
     * @param object an object
     *
     * @throws IllegalArgumentException if the specified object does not
     * implement <code>Serializable</code>
     */
    public WrappedSerializable(T object) {
	if (object == null) {
	    throw new NullPointerException("obj is null");
	} else if (!(object instanceof Serializable)) {
	    throw new IllegalArgumentException("obj not serializable");
	}
	ManagedObject managedObj =
	    (object instanceof ManagedObject) ?
	    (ManagedObject) object :
	    new Wrapper<T>(object);
	    
	ref = AppContext.getDataManager().createReference(managedObj);
    }

    /**
     * Returns the object in this wrapper.
     *
     * @return T the object in this wrapper
     *
     * @throws IllegalStateException if {@link #remove remove} has
     * been invoked on this instance
     */
    public T get() {
	checkRemoved();
	ManagedObject obj = (ManagedObject) ref.get();
	if (obj instanceof Wrapper) {
	    Wrapper<T> wrapper = Objects.uncheckedCast(obj);
	    return wrapper.get();
	} else {
	    @SuppressWarnings("unchecked")
	    T result = (T) obj;
	    return result;
	}
    }

    /**
     * Marks this instance as removed, and if this instance contains a
     * {@link ManagedObject} wrapper to the {@code object} specified
     * during construction, then removes the wrapper as well.
     *
     * @throws IllegalStateException if {@link #remove remove} has
     * been invoked on this instance
     */
    public void remove() {
	checkRemoved();
	Object obj = ref.get();
	if (obj instanceof Wrapper) {
	    AppContext.getDataManager().removeObject(obj);
	}
	ref = null;
    }

    /**
     * Throws {@code IllegalStateException} if {@code remove} has
     * already been invoked.
     */
    private void checkRemoved() {
	if (ref == null) {
	    throw new IllegalStateException("remove already invoked");
	}
    }

    /**
     * Managed object wrapper for a serializable object.
     */
    private static class Wrapper<T> implements ManagedObject, Serializable {

	private static final long serialVersionUID = 1L;

	private final T obj;

	Wrapper(T obj) {
	    this.obj = obj;
	}

	T get() {
	    return obj;
	}
    }
}
