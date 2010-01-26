/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 * --
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.util.ManagedSerializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * An {@code AdaptiveField} is an abstraction over the traditional Java field
 * that allows a field to be stored either locally (as a normal field would be)
 * or remotely in the data store, while still allowing common operations as
 * well as the ability to dynamically switch where the field's value is stored.
 *
 * <p>
 *
 * A {@code ManagedObject} will deserialize all of its non-transient fields,
 * which at times is undesirable for latency (e.g. when the field is large, or
 * will not be used).  An {@code AdaptiveField} allow the developer finer
 * control over whether a field needs to be included in the serialization graph
 * by providing a common interface for interacting with the field regardless of
 * how it is stored.
 *
 * <p>
 *
 * A local {@code AdaptiveField} has its value stored just like any other Java
 * field.  When a {@code ManagedObject} deserializes from the data store, the
 * field will be included in its serialization graph.  For this reason a {@code
 * ManagedObject} <i>cannot</i> be stored in an {@code AdaptiveField}.  If the
 * developer wants to change the field so that it is stored in the data store,
 * the {@link AdaptiveField#makeManaged AdaptiveField.makeManaged} call can be
 * used.  This causes the field's value to no longer be deserialized when the
 * containing {@code ManagedObject} is deserialized.
 *
 * @param <T> the type of the referenced object
 * @see ManagedReference
 * @see com.sun.sgs.app.ManagedObject
 */
public final class AdaptiveField<T> implements Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 0x1L;

    /**
     * The field if is being stored locally, else null.
     *
     * @serial
     */
    private T local;

    /**
     * The reference to the field if it is current being managed by the data
     * store, else null.
     *
     * @serial
     */
    private ManagedReference<ManagedSerializable<T>> ref;

    /**
     * A local cache of the object if it is being managed by the data store but
     * has accessed during this transaction, else null.  This allows subsequent
     * calls to return immediately instead of having to go to the {@code
     * ManagedReference} cache.
     */
    private transient T remoteCache;

    /**
     * Whether the field is currently maintained with a local reference or is
     * being stored in the data store.
     *
     * @serial
     */
    private boolean isLocal;

    /**
     * Constructs this {@code AdaptiveField} as a local reference with the
     * provided value.
     *
     * @param value the value of this field
     *
     * @throws IllegalArgumentException if the provided value is a {@code
     *         ManagedObject}
     */
    public AdaptiveField(T value) {
	this(value, true);
    }

    /**
     * Constructs this {@code AdaptiveField} with the provided value, and
     * stores it as specified.
     *
     * @param value the value of this field
     * @param isLocal whether this field should be kept with a local reference
     *        or stored in the data store.
     *
     * @throws IllegalArgumentException if the provided value is a {@code
     *         ManagedObject}
     */
    public AdaptiveField(T value, boolean isLocal) {

	this.isLocal = isLocal;

	if (isLocal) {
	    local = value;
	} else {
	    remoteCache = value;
	    ref = AppContext.getDataManager().createReference(
		new ManagedSerializable<T>(value));
	}
    }

    /**
     * Returns the value of the field.  Note that if the field is stored
     * locally and changes, the caller should call {@link
     * com.sun.sgs.app.DataManager#markForUpdate DataManager.markForUpdate} on
     * the {@code ManagedObject} that contains this field since its state has
     * been changed.  The values of fields that are not local are cached after
     * the initial call to the {@code DataManager}, so subsequent calls to
     * {@code get} will act as if they are local.
     *
     * @return the value of the field
     */
    public T get() {
	if (isLocal) {
	    return local;
	}
	if (remoteCache == null && ref != null) {
	    remoteCache = ref.get().get();
	}
	return remoteCache;
    }

    /**
     * Returns the value of the field and if remotely stored, marks the value
     * for update.  Note that if the field is stored locally and changes, the
     * caller should call {@link com.sun.sgs.app.DataManager#markForUpdate
     * DataManager.markForUpdate} on the {@code ManagedObject} that contains
     * this field since its state has been changed.
     *
     * @return the value of the field
     */
    public T getForUpdate() {
	if (isLocal) {
	    return local;
	}
	if (ref != null) {
	    remoteCache = ref.getForUpdate().get();
	}
	return remoteCache;
    }

    /**
     * Returns {@code true} if this field is stored as a local reference.
     * Otherwise, the field is persisted with the {@code DataManager} and is
     * excluded from the serialization graph of the object that contains this
     * {@code AdaptiveField}.
     *
     * @return {@code true} if this field is stored as a local reference
     */
    public boolean isLocal() {
	return isLocal;
    }

    /**
     * Move this field from being stored as a {@code ManagedObject} in the data
     * store to being kept as a local reference.  This field will now be
     * included in the serialization graph of the object that contains this
     * field.
     */
    public void makeLocal() {
	if (!isLocal) {
	    if (ref != null) {
		ManagedSerializable<T> m = ref.get();
		// REMINDER: if we can ever remote the object without having to
		// call get(), then we could use to localCache to speed this
		// next call up a bit.
		local = m.get();
		AppContext.getDataManager().removeObject(m);
	    } else {
		// If the field was stored remotely and ref == null, then the
		// value should be null, as we remove the ManagedSerializable
		// from the data store when a remotely managed object is set to
		// null
		local = null;
	    }
	    ref = null;
	    remoteCache = null;
	    isLocal = true;
	}
    }

    /**
     * Move this field from being a local Java reference to being stored as a
     * {@code ManagedObject} in the data store.  This field will no longer be
     * included in the serialization graph of the object that contains this
     * field.
     */
    public void makeManaged() {
	if (isLocal) {
	    ref = AppContext.getDataManager().
		createReference(new ManagedSerializable<T>(local));
	    remoteCache = local;
	    local = null;
	    isLocal = false;
	}
    }

    /**
     * If this field is not stored locally, marks the field for update.
     */
    public void markForUpdate() {
	if (!isLocal && ref != null) {
	    AppContext.getDataManager().markForUpdate(ref.get());
	}
    }

    /**
     * Sets the value of this field.
     *
     * @param value the new value of the field
     */
    public void set(T value) {
	set(value, isLocal);
    }

    /**
     * Sets the value of this field and updates its locality based on {@code
     * isLocal}.
     *
     * @param value the new value of the field
     * @param isLocal whether this field should be kept with a local reference
     *        or stored in the data store.
     */
    public void set(T value, boolean isLocal) {
	if (isLocal) {
	    local = value;
	    // check whether it was previously managed object and if so, remove
	    // it
	    if (!this.isLocal) {
		this.isLocal = true;
		if (ref != null) {
		    AppContext.getDataManager().removeObject(ref.get());
		    ref = null;
		    remoteCache = null;
		}
	    }
	} else {
	    // invalidate the local copy and move this field into the data
	    // store
	    if (this.isLocal) {
		this.isLocal = false;
		local = null;
		// null remote values are never stored in the data store, and
		// the ref should already be null at this point
		if (value != null) {
		    ref = AppContext.getDataManager().createReference(
			new ManagedSerializable<T>(value));
		}
	    } else {
		// the value was already remotely managed, so attempt to reuse
		// the old ManagedSerializable
		if (ref == null) {
		    if (value != null) {
			// if the previously the remote value was null, we need
			// to create a new managed reference
			ref = AppContext.getDataManager().createReference(
			    new ManagedSerializable<T>(value));
		    }
		} else if (value != null) {
		    // we can reuse the previous ManagedSerializable to hold
		    // the value
		    ref.get().set(value);
		} else {
		    // else, the value is null but we had an old value stored
		    // by reference, so remove it from the data store
		    AppContext.getDataManager().removeObject(ref.get());
		    ref = null;
		}
	    }
	    remoteCache = value;
	}
    }

    /** Make sure that the cached value is cleared. */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException {

	s.defaultReadObject();
	remoteCache = null;
    }
}
