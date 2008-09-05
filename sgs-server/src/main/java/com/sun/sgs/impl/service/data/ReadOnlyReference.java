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
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.app.annotation.ReadOnly;

import com.sun.sgs.impl.sharedutil.Objects;

import com.sun.sgs.kernel.AccessReporter.AccessType;

import java.io.ObjectStreamException;
import java.io.Serializable;

import java.math.BigInteger;

/**
 * An implementation of {@code ManagedReference} that accesses {@code
 * ManagedObject} instances that have been annotated with {@link ReadOnly}.
 * This class works in tandem with the run-time system's {@link DataCache}
 * implementation if object caching is enabled.
 *
 * <p>
 *  
 * Attempts to modify the object by {@link #getForUpdate()}, {@link
 * #markForUpdate} or {@link #remove()} will all throw an {@link
 * UnsupportedOperationException}.  This class makes no guarantees about
 * detecting modifications to the object referred to by this reference outside
 * of these calls.  
 *
 * <p>
 *
 * Instances of this class are created for a single transaction.  Within the
 * context of that transaction only one canonical instance will be created for
 * an object based on that object's id.  As new {@code ReadOnlyReference}
 * instances are created they are registered with the {@link
 * ReadOnlyReferenceTable}, which maintains the tranaction-specific mappings
 * from id to reference.  The {@code ReadOnlyReferenceTable} should be
 * considered a logical part of this class in that it maintains all the shared
 * state for all instances created during a single transaction.
 * 
 *
 * @see DataCache
 * @see ReadOnlyReferenceTable
 * @see ReadOnlyDataCache
 */
final class ReadOnlyReference<T> 
    implements InternalManagedReference<T>, Serializable {

    private static enum State {
	FETCHED,
	EMPTY,
	REMOVED
    }

    private static final long serialVersionUID = 2;

    /**
     * The object identifier used to locate the {@code ManagedObject} in the
     * backing data store
     *
     * @serial
     */
    final long oid;

    /**
     * The identifier of this reference 
     */
    private transient BigInteger id;

    /**
     * The context of the current transaction.  This is initialized when the
     * reference is first created or when it is deserialized.
     */
    private transient Context context;

    /**
     * The object to which this reference refers
     */
    private transient ManagedObject object;

    /**
     * The state of this reference.  This is used to determine whether a
     * reference's object has already been removed during the current
     * transaction.
     */
    private transient State state;

    /**
     * Constructs an empty {@code ReadOnlyReference} that refers to the {@code
     * ManagedObject} with the provided object identifier.
     */
    private ReadOnlyReference(Context context, long oid) {
	this.context = context;
 	this.oid = oid;
 	object = null;
 	state = State.EMPTY;
    }
    
    /**
     * Constructs a new {@code ReadOnlyReference} and stores the provided
     * {@code object} in the data store.  This constructor should only be used
     * the very first time an object has a {@code ReadOnlyReference} created
     * for it.  If this constructor were to be used more than once, then a
     * single object would have multiple oids associated with it.
     *
     * @param context the context of the current transactions
     * @param object the object to which this reference should refer
     */
    private ReadOnlyReference(Context context, T object) {
	this.context = context;
	oid = context.store.createObject(context.txn);	
	this.object = (ManagedObject)object;
	state = State.FETCHED;

	// since this is the very first time the object has had a reference
	// created for it, persist it in the data store so any
	// ReadOnlyReference instances on other nodes can load it into their
	// node's cache.
	byte[] serialized = SerialUtil.serialize((ManagedObject)object,
						 context.classSerial);
	context.store.setObject(context.txn, oid, serialized);
    }
    
    /*
     * Implement ManagedReference
     */

    /**
     * {@inheritDoc}
     */
    public T get() {
	context.oidAccesses.reportObjectAccess(getId(), AccessType.READ);
	switch (state) {
	case FETCHED:
	    // This is a no-op since in this state this instance already has a
	    // value
	    break;
	case EMPTY:
	    // This instance was created based on an object id, so load the
	    // data for this object either from the node-local data cache, or
	    // if this is the first time this id has been loaded on this node,
	    // from the backing store
	    ManagedObject tempObject;
	    if (context.cache.contains(oid)) {
		tempObject = context.cache.lookup(oid);
	    }
	    else {
		tempObject = deserialize(context.store.
					 getObject(context.txn, oid, false));
		// store the object in the cache for future instances
		context.cache.cacheObject(oid, tempObject);
	    }

	    object = tempObject;
	    state = State.FETCHED; 

	    // the first time we load in an object from the data store, we mark
	    // in our reference table that all future references created for
	    // this object in the current transaction will refer to this
	    // instance instead of creating a new one
	    context.readOnlyRefs.put(object, this);
	    break;
	case REMOVED:
	    throw new ObjectNotFoundException("The object has already been " +
					      "removed");
	}

	@SuppressWarnings("unchecked")
	T result = (T) object;
	return result;
    }

    /**
     * Throws an {@code IllegalStateException}, as {@code ReadOnlyReferences}
     * cannot be updated.
     *
     * @return {@inheritDoc}
     *
     * @throws IllegalStateException if called
     */
    public T getForUpdate() {
	throw new UnsupportedOperationException(
	    "Cannot mark reference to a read-only object for update");
    }

    /**
     * {@inheritDoc}
     */
    public BigInteger getId() {
	if (id == null) {
	    id = BigInteger.valueOf(oid);
	}
	return id;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object object) {
	return (object != null && object instanceof ReadOnlyReference)
	    ? oid == ((ReadOnlyReference)object).oid
	    : false;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
	return (int)oid;
    }


    /*
     * Implement InternalManagedReference
     */
    
    /**
     * {@inheritDoc}
     */
    public long getOid() {
	return oid;
    }
    
    /**
     * {@inheritDoc}
     */
    public void markForUpdate() {
	throw new UnsupportedOperationException(
	    "Cannot mark reference to a read-only object for update");
    }

    /**
     * {@inheritDoc}
     */
    public void removeObject() {
	throw new UnsupportedOperationException(
	    "Cannot remove a read-only reference");	
    }

    /**
     * {@inheritDoc}
     */
    public void setObject(ManagedObject o) {

	switch (state) {
	case EMPTY:
	    // update the object in the cache for future instances.  Note that
	    // even if this call is redundant, the access in will keep the
	    // object valid in cache.
	    context.cache.cacheObject(oid, o);
	    
	    // mark in the reference table that any future references to the
	    // provided object should return this reference.  This update is
	    // guaranteed to be unique since setObject() can never be called
	    // twice.
	    context.readOnlyRefs.put(object, this);
	    object = o;
	    state = State.FETCHED; 
	default:
	    throw new IllegalStateException(
		"Cannot set the value of a ReadOnlyReference after the " +
		"value has already been set");
	}
    }

    /*
     * Package-private methods that Context uses to create and look up new
     * references of this type
     */

    /**
     * Returns any {@code ReadOnlyReference} created for the provided {@code
     * object} or {@code null} if no reference has been created for the object.
     * This method will not create a new reference.
     *
     * @param context the context of the current transaction
     * @param object the object refered to by any returned reference
     *
     * @return the {@code ReadOnlyReference} associated with the
     *         object or {@code null} if one was not found
     */
    static <T> ReadOnlyReference<T> getReference(Context context, T object) {
	checkReadOnly(object);

	// look in the reference table to see whether we have already created a
	// new ReadOnlyReference for this object.
	ReadOnlyReference<T> ref = Objects.
	    uncheckedCast(context.readOnlyRefs.get((ManagedObject)object));
	
	return ref;
    }

    
    /**
     *
     *
     * @param context the context of the current transaction
     * @param object the object refered to by the returned reference
     *
     * @return the canonical reference for the provided object
     */
    static <T> ReadOnlyReference<T> getOrCreateIfNotPresent(Context context, T object) {
	checkReadOnly(object);
	
	// look in the reference table to see whether we have already created a
	// new ReadOnlyReference for this object.
	ReadOnlyReference<T> ref = Objects.
	    uncheckedCast(context.readOnlyRefs.get((ManagedObject)object));

	// if we haven't already created a reference for this object within the
	// current transaction
	if (ref == null) {
	    ref = new ReadOnlyReference<T>(context, object);

	    // update the reference table so any future calls to create a
	    // reference for this object during this transaction will return
	    // this instasnce
	    context.readOnlyRefs.put((ManagedObject)object, ref);
	}
	// check that the reference hasn't already been removed
	else if (ref.isRemoved()) {
	    throw new ObjectNotFoundException("Object has been removed");
	}

	return ref;
    }

    /**
     *
     *
     * @param context
     * @param oid
     *
     * @return 
     */
    static ReadOnlyReference<?> getOrCreateIfNotPresent(Context context, long oid) {

	// look in the reference table to see if we have already created a
	// ReadOnlyReference with this oid.  If so, we want to use the same
	// reference during the transaction.
	ReadOnlyReference<?> ref = context.readOnlyRefs.get(oid);

	// if we haven't already created a reference for this oid, then create
	// one now
	if (ref == null) {
	    
	    ref = new ReadOnlyReference(context, oid);

	    // update the reference table so that any future calls during this
	    // transaction to get a reference for this oid will return this
	    // instance.
	    context.readOnlyRefs.put(oid, ref);
	}
	// If we had already created a reference for this oid within the
	// current transaction, ensure that we haven't asked for a reference to
	// an object that has just been removed.
	else if (ref.state.equals(State.REMOVED)) {
	    throw new ObjectNotFoundException("Object has been removed");
	}
	
	return ref;
    }


    /**
     * Returns {@code true} if the object refered to by this reference has been
     * removed during the current transaction.  Note that even if this method
     * returns {@code false}, a subsequent call to {@link #get()} may still
     * throw an {@link ObjectNotFoundException} if the object was remove during
     * a different transaction.
     *
     * @return {@code true} if the object refered to by this reference
     *         has been removed during the current transaction
     */
    boolean isRemoved() {
	return state.equals(State.REMOVED);
    }

    /**
     * Returns the object refered to by this reference if it has already been
     * loaded from the cache or datastore, or returns {@code null} if this
     * instance was created with just an oid and has not yet loaded the object.
     *
     * @return the object refered to by this reference or {@null} if
     *         it has not been accessed
     */
    ManagedObject getObject() {
	return object;
    }

    private static boolean isReadOnly(Object o) {
	return o.getClass().getAnnotation(ReadOnly.class) != null;
    }

    private static void checkReadOnly(Object o) {
	if (!isReadOnly(o))
	    throw new IllegalArgumentException("FILL IN LATER");
    }


    /** 
     * Returns this instance if this is the first time a reference for this
     * instance's {@code oid} has been created, or replaces this instance with
     * a canonical instance if an instance had been created prior.
     *
     * @return the canonical instance for the oid
     */
    private Object readResolve() throws ObjectStreamException {
	context = DataServiceImpl.getContextNoJoin();
	state = State.EMPTY;
	
	// see if another instance for this reference's oid has
	// already been created
	ReadOnlyReference ref = context.readOnlyRefs.get(oid);
	if (ref == null) {
	    // if not, this instance will become the canonical
	    // instance, so update the reference table
	    context.readOnlyRefs.put(oid, this);
	    return this;
	} else {
	    // return the instance that had been created before us
	    return ref;
	}
    }


    /**
     * Returns the managed object associated with serialized data and checks
     * that the return value is not {@code null} and has as {@link ReadOnly}
     * annotation.
     *
     * @param data
     *
     * @return 
     *
     * @throws ObjectIOException if 
     */
    private ManagedObject deserialize(byte[] data) {
	Object obj = SerialUtil.deserialize(data, context.classSerial);
	if (obj == null) {
	    throw new ObjectIOException(
		"Managed object must not deserialize to null", false);
	} 
	else if (!(obj instanceof ManagedObject)) {
	    throw new ObjectIOException(
		"Deserialized object must implement ManagedObject", false);
	}
	else if (!(isReadOnly(obj))) {
	    throw new ObjectIOException(
		"Deserialized object must have ReadOnly annotation", false);
	}
	return (ManagedObject) obj;
    }

}
