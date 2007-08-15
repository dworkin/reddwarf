package com.sun.sgs.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;

/**
 * An {@code AdaptiveField} is an abstraction over the traditional
 * Java field that allows a field to be stored either locally (as a
 * normal field would be) or remotely in the data store, while still
 * allowing common operations as well as the ability to dynamically
 * switch where the field's value is stored.
 *
 * <p>
 *
 * A {@code ManagedObject} will deserialize all of its non-transient
 * fields, which at times is undesireable for latency (e.g. when the
 * field is large, or will not be used).  An {@code AdaptiveField}
 * allow the developer finer control over whether a field needs to be
 * included in the serialization graph by providing a common interface
 * for interacting with the field regardless of how it is stored. 
 *
 * <p> 
 *
 * A local {@code AdaptiveField} has its stored just like any other
 * Java field.  When a {@code ManagedObject} deserializes from the
 * data store, the field will be included in its serialization graph.
 * For this reason a {@code ManagedObject} <i>cannot</i> be stored in
 * an {@code AdaptiveField}.  If the developer wants to change the
 * field so that it is stored in the data store, the {@link
 * AdaptiveField#makeManaged()} call can be used.  This causes the
 * field's value to no longer be deserialized when the containing
 * {@code ManagedObject} is deserialized.
 *
 *
 * @version 1.0
 * 
 * @see ManagedReference
 * @see ManagedObject
 */
public final class AdaptiveField<T> implements java.io.Serializable {

    private static final long serialVersionUID = 0x1L;

    /**
     * The field if is being stored locally.
     */
    private T local;

    /**
     * The reference to the field if it is current being managed by
     * the data store.
     */
    private ManagedReference ref;

    /**
     * A local cache of the object if it is being managed by the data
     * store but has accessed during this transaction.  This allows
     * subsequence calls to return immediately instead of having to go
     * to the {@code ManagedReference} cache.
     */
    private transient T remoteCache;
    
    /**
     * Whether the field is currently maintained with a local
     * reference or is being stored in the data store.
     */
    private boolean isLocal;

    /**
     * Constructs this {@code AdaptiveField} as a local reference with
     * the provided value.
     *
     * @param value the value of this field
     *
     * @throws IllegalArgumentException if the provided value is a
     *         {@code ManagedObject}.
     */
    public AdaptiveField(T value) {
	this(value, true);
    }

    /**
     * Constructs this {@code AdaptiveField} with the provided value,
     * and stores it as specified
     *
     * @param value the value of this field
     * @param isLocal whether this field should be kept with a local
     *        reference or stored in the data store.
     *
     * @throws IllegalArgumentException if the provided value is a
     *         {@code ManagedObject}.
     */
    public AdaptiveField(T value, boolean isLocal) {

	this.isLocal = isLocal;

	if (isLocal) {
	    local = value;	    
	    ref = null;
	}
	else {
	    local = null;
	    remoteCache = value;
	    ref = AppContext.getDataManager().
		createReference(new ManagedSerializable<T>(value));
	}
    }

    /**
     * Returns the value of the field.  Note that if the field is
     * stored locally and changes, the caller should call {@link
     * DataManager#markForUpdate(ManagedObject} on the {@code
     * ManagedObject} that contains this field since its state has
     * been changed.  The values of fields that are not local are
     * cached after the initial call to the {@code DataManager}, so
     * subsequent calls to {@code get()} will act as if they are
     * local.
     *
     * @return the value of the field
     */
    @SuppressWarnings({"unchecked"})
    public T get() {
	if (isLocal)
	    return local;
	else {
	    return (remoteCache == null && ref != null)
		? (remoteCache = ((ManagedSerializable<T>)
				  (ref.get(ManagedSerializable.class))).get())
		: remoteCache;
	}
    }

    /**
     * Returns the value of the field and if remotely stored, marks
     * the value for update.  Note that if the field is stored locally
     * and changes, the caller should call {@link
     * DataManager#markForUpdate(ManagedObject} on the {@code
     * ManagedObject} that contains this field since its state has
     * been changed.
     *
     * @return the value of the field
     */
    @SuppressWarnings({"unchecked"})
    public T getForUpdate() {
	if (isLocal)
	    return local;
	else {
	    if (ref != null) {
		ManagedSerializable<T> m = 
		    ref.get(ManagedSerializable.class);
		AppContext.getDataManager().markForUpdate(m);
	    }

	    return (remoteCache == null && ref != null)
		? (remoteCache = ((ManagedSerializable<T>)
				  (ref.get(ManagedSerializable.class))).get())
		: remoteCache;
	}		
    }

    /**
     * Returns {@code true} if this field is stored as a local Java
     * reference.  Otherwise, the field is persisted with the {@code
     * DataManager} and is excluded from the serialization graph of
     * the object that contains this {@code AdaptiveField}.
     *
     * @return {@code true} if this field is stored as a local Java
     *          reference
     */
    public boolean isLocal() {
	return isLocal;
    }

    /**
     * Move this field from being stored as a {@code ManagedObject} in
     * the data store to being kept as a local Java reference.  This
     * field will now be included in the serialization graph of the
     * object that contains this field. 
     */
    @SuppressWarnings({"unchecked"})
    public void makeLocal() {
	if (!isLocal) {
	    if (ref != null) {
		ManagedSerializable<T> m = ref.get(ManagedSerializable.class);
		// REMINDER: if we can ever remote the object without
		// having to call get(), then we could use to
		// localCache to speed this next call up a bit.
		local = m.get();
		AppContext.getDataManager().removeObject(m);
	    }
	    // If the field was stored remotely and ref == null, then
	    // the value should be null, as we remove the
	    // ManagedSerializable from the data store when a remotely
	    // managed object is set to null
	    else 
		local = null;	    
	    ref = null;
	    isLocal = true;
	}
    }

    /**
     * Move this field from being a local Java reference to being
     * stored as a {@code ManagedObject} in the data store.  This
     * field will no longer be included in the serialization graph of
     * the object that contains this field.  
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
     * If this field is not stored locally, marks the field for
     * update.
     */
    public void markForUpdate() {
	if (!isLocal && ref != null)
	    AppContext.getDataManager().
		markForUpdate(ref.get(ManagedSerializable.class));
    }

    /** 
     * Sets the value of this field.
     *     
     * @param value the new value of the field
     */
    @SuppressWarnings({"unchecked"})
    public void set(T value) {
	set(value, isLocal);
    }

    /**
     * Sets the value of this field and updates its locality based on
     * {@code isLocal}.
     *
     * @param value the new value of the field
     * @param isLocal whether this field should be kept with a local
     *        reference or stored in the data store.
     */
    @SuppressWarnings({"unchecked"})
    public void set(T value, boolean isLocal) {
	if (isLocal) {
	    local = value;
	    // check whether it was previously managed object and if
	    // so, remove it
 	    if (this.isLocal != isLocal) {
		ManagedSerializable<T> m = 
		    (ManagedSerializable<T>)(ref.get(ManagedSerializable.class));
		AppContext.getDataManager().removeObject(m);
		this.isLocal = isLocal;
	    }
	}
	else {
	    // invalidate the local copy and move this field into the
	    // data store
	    if (this.isLocal) {
		local  = null;
		this.local = local;
		remoteCache = value;
		// null remote values are never stored in the data
		// store, and the ref should already be null at this
		// point
		if (value != null)
		    ref = AppContext.getDataManager().
			createReference(new ManagedSerializable<T>(value));
	    }
	    // the value was already remotely managed
	    else {
		// previously the remote value was null, so we need to
		// create a new managed reference
		if (ref == null && value != null)
		    ref = AppContext.getDataManager().
			createReference(new ManagedSerializable<T>(value));
		// we can reuse the previous ManagedSeriazable to hold
		// the value
		else if (value != null) {
		    ManagedSerializable<T> m = 
			ref.get(ManagedSerializable.class);
		    m.set(value);
		}
		// else, the value is null but we had an old value
		// stored by reference, so remove it from the data
		// store
		else 
		    AppContext.getDataManager().
			removeObject(ref.get(ManagedSerializable.class));
		

		local = null;
		remoteCache = value;
		this.isLocal = isLocal;
	    }
	}
    }    
}