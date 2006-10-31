package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.util.LoggerWrapper;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Provides an implementation of ManagedReference. */
final class ManagedReferenceImpl<T extends ManagedObject>
    implements ManagedReference<T>, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The logger for this class. */
    private final static LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ManagedReferenceImpl.class.getName()));

    /**
     * The possible states of a reference.
     *
     * Here's a table relating state values to the values of the object and
     * fingerprint fields, as well as whether the reference should be present
     * in the ReferenceTable:
     *
     *   State		 object    fingerprint  ReferenceTable
     *   NEW		 non-null  null		present
     *   EMPTY		 null      null		present
     *   NOT_MODIFIED	 non-null  null		present
     *   MAYBE_MODIFIED  non-null  non-null	present
     *   MODIFIED	 non-null  null		present
     *	 FLUSHED	 null      null		present
     *	 REMOVED	 null      null		not present
     */
    private static enum State {

	/** A object created in this transaction. */
	NEW,

	/**
	 * A reference to an existing object that has not been dereferenced
	 * yet.
	 */
	EMPTY,

	/**
	 * An object that has been read and will be marked explicitly when
	 * modified.
	 */
	NOT_MODIFIED,

	/**
	 * An object that has been read and will be checked for modification at
	 * commit.
	 */
	 MAYBE_MODIFIED,

	/** An object that has been explicitly marked modified. */
	MODIFIED,

	/**
	 * An object whose contents have been flushed to the database during
	 * transaction preparation.
	 */
	FLUSHED,

	/** An object that has been removed. */
	REMOVED
    };

    /**
     * Information related to the transaction in which this reference was
     * created.  This field is not final only so that it can be set during
     * deserialization.
     */
    private transient Context context;

    /**
     * The object ID.
     *
     * @serial
     */
    final long oid;

    /** The associated object or null. */
    private transient T object;

    /** The fingerprint of the object before it was modified, or null. */
    private transient byte[] fingerprint;

    /** The current state. */
    private transient State state;

    /* -- Getting instances -- */

    /**
     * Returns the reference associated with a context and object, or null if
     * the reference is not found.
     */
    static <T extends ManagedObject> ManagedReferenceImpl<T> findReference(
	Context context, T object)
     {
	 return context.refs.find(object);
     }

    /**
     * Returns the reference associated with a context and object, creating a
     * NEW reference if none is found.
     */
    static <T extends ManagedObject> ManagedReferenceImpl<T> getReference(
	Context context, T object)
     {
	 ManagedReferenceImpl<T> ref = context.refs.find(object);
	 if (ref == null) {
	     ref = new ManagedReferenceImpl<T>(context, object);
	     context.refs.add(ref);
	 }
	 if (logger.isLoggable(Level.FINER)) {
	     logger.log(Level.FINER, "getReference object:{0} returns {1}",
			object, ref);
	 }
	 return ref;
     }

    /**
     * Returns the reference associated with a context and object ID, creating
     * an EMPTY reference if none is found.
     */
    static ManagedReferenceImpl<? extends ManagedObject> getReference(
	Context context, long oid)
     {
	 ManagedReferenceImpl<? extends ManagedObject> ref =
	     context.refs.find(oid);
	 if (ref == null) {
	     ref = new ManagedReferenceImpl<ManagedObject>(context, oid);
	     context.refs.add(ref);
	 }
	 if (logger.isLoggable(Level.FINER)) {
	     logger.log(Level.FINER, "getReference oid:{0} returns {1}",
			oid, ref);
	 }
	 return ref;
     }

    /** Creates a NEW reference to an object. */
    private ManagedReferenceImpl(Context context, T object) {
	this.context = context;
	oid = context.store.createObject(context.txn);
	this.object = object;
	state = State.NEW;
	validate();
    }

    /** Creates an EMPTY reference to an object ID. */
    private ManagedReferenceImpl(Context context, long oid) {
	this.context = context;
	this.oid = oid;
	state = State.EMPTY;
	validate();
    }

    /* -- Methods for DataService -- */

    void removeObject() {
	switch (state) {
	case EMPTY:
	case NOT_MODIFIED:
	case MAYBE_MODIFIED:
	case MODIFIED:
	    context.store.removeObject(context.txn, oid);
	    /* Fall through */
	case NEW:
	    context.refs.remove(this);	    
	    object = null;
	    fingerprint = null;
	    state = State.REMOVED;
	    break;
	case FLUSHED:
	    throw new TransactionNotActiveException(
		"No transaction is in progress");
	case REMOVED:
	    throw new ObjectNotFoundException("The object is not found");
	default:
	    throw new AssertionError();
	}
    }

    void markForUpdate() {
	switch (state) {
	case EMPTY:
	    object = deserialize(
		context.store.getObject(context.txn, oid, true));
	    context.refs.registerObject(this);
	    state = State.MODIFIED;
	    break;
	case MAYBE_MODIFIED:
	    fingerprint = null;
	    /* Fall through */
	case NOT_MODIFIED:
	    context.store.markForUpdate(context.txn, oid);
	    state = State.MODIFIED;
	    break;
	case MODIFIED:
	case NEW:
	    break;
	case FLUSHED:
	    throw new TransactionNotActiveException(
		"No transaction is in progress");
	case REMOVED:
	    throw new ObjectNotFoundException("The object is not found");
	default:
	    throw new AssertionError();
	}
    }

    /* -- Implement ManagedReference<T> -- */

    public T get() {
	DataServiceImpl.checkContext(context);
	switch (state) {
	case EMPTY:
	    object = deserialize(
		context.store.getObject(context.txn, oid, false));
	    context.refs.registerObject(this);
	    if (context.detectModifications) {
		fingerprint = SerialUtil.fingerprint(object);
		state = State.MAYBE_MODIFIED;
	    } else {
		state = State.NOT_MODIFIED;
	    }
	    break;
	case NEW:
	case NOT_MODIFIED:
	case MAYBE_MODIFIED:
	case MODIFIED:
	    break;
	case FLUSHED:
	    throw new TransactionNotActiveException(
		"No transaction is in progress");
	case REMOVED:
	    throw new ObjectNotFoundException("The object is not found");
	default:
	    throw new AssertionError();
	}
	return object;
    }

    public T getForUpdate() {
	DataServiceImpl.checkContext(context);
	switch (state) {
	case EMPTY:
	    object = deserialize(
		context.store.getObject(context.txn, oid, true));
	    context.refs.registerObject(this);
	    state = State.MODIFIED;
	    break;
	case NOT_MODIFIED:
	case MAYBE_MODIFIED:
	    context.store.markForUpdate(context.txn, oid);
	    state = State.MODIFIED;
	    break;
	case FLUSHED:
	    throw new IllegalStateException("Update flushed");
	case NEW:
	case MODIFIED:
	    break;
	case REMOVED:
	    throw new ObjectNotFoundException("The object is not found");
	default:
	    throw new AssertionError();
	}
	return object;
    }

    /* -- Implement Serializable -- */

    /** Replaces this instance with a canonical instance. */
    private Object readResolve() throws ObjectStreamException {
	try {
	    context = DataServiceImpl.getContext();
	    state = State.EMPTY;
	    validate();
	    ManagedReferenceImpl ref = context.refs.find(oid);
	    if (ref == null) {
		context.refs.add(this);
		return this;
	    } else {
		return ref;
	    }
	} catch (RuntimeException e) {
	    throw (InvalidObjectException) new InvalidObjectException(
		e.getMessage()).initCause(e);
	}
    }

    /* -- Object methods -- */

    public boolean equals(Object object) {
	/* Compare identity, since instances are canonicalized. */
	return this == object;
    }

    public int hashCode() {
	return (int) (oid >>> 32) ^ (int) oid;
    }

    public String toString() {
	return "ManagedReferenceImpl[oid:" + oid + ", state:" + state + "]";
    }

    /* -- Other methods -- */

    /**
     * Checks the fields of this object to make sure they have valid values,
     * throwing IllegalStateException if a problem is found.
     */
    void checkState() {
	switch (state) {
	case NEW:
	    if (object == null) {
		throw new IllegalStateException("NEW with no object");
	    } else if (fingerprint != null) {
		throw new IllegalStateException("NEW with fingerprint");
	    }
	    break;
	case EMPTY:
	case REMOVED:
	case FLUSHED:
	    if (object != null) {
		throw new IllegalStateException(state + " with object");
	    } else if (fingerprint != null) {
		throw new IllegalStateException(state + " with fingerprint");
	    }
	    break;
	case NOT_MODIFIED:
	case MODIFIED:
	    if (object == null) {
		throw new IllegalStateException(state + " with no object");
	    } else if (fingerprint != null) {
		throw new IllegalStateException(state + " with fingerprint");
	    }
	    break;
	case MAYBE_MODIFIED:
	    if (object == null) {
		throw new IllegalStateException(
		    "MAYBE_MODIFIED with no object");
	    } else if (fingerprint == null) {
		throw new IllegalStateException(
		    "MAYBE_MODIFIED with no fingerprint");
	    }
	    break;
	default:
	    throw new AssertionError();
	}
    }

    /**
     * Stores any modifications to the data store, and changes the state to
     * FLUSHED.
     */
    void flush() {
	switch (state) {
	case FLUSHED:
	case REMOVED:
	    break;
	case NEW:
	case MODIFIED:
 	    context.store.setObject(
		context.txn, oid, SerialUtil.serialize(object));
	    object = null;
	    fingerprint = null;
	    state = State.FLUSHED;
	    break;
	case MAYBE_MODIFIED:
	    if (!SerialUtil.matchingFingerprint(object, fingerprint)) {
		context.store.setObject(
		    context.txn, oid, SerialUtil.serialize(object));
	    }
	    /* Fall through */
	case NOT_MODIFIED:
	    object = null;
	    fingerprint = null;
	    state = State.FLUSHED;
	    break;
	default:
	    throw new AssertionError();
	}
    }

    /**
     * Checks if the object has been marked removed.  This method will return
     * false if the object was not removed in this transaction.
     */
    boolean isRemoved() {
	return state == State.REMOVED;
    }

    /** Returns the object currently associated with this reference or null. */
    T getObject() {
	return object;
    }

    /** Validates the values of the context and oid fields. */
    private void validate() {
	if (context == null) {
	    throw new NullPointerException("The context must not be null");
	}
	if (oid < 0) {
	    throw new IllegalArgumentException("The oid must not be negative");
	}
    }

    /**
     * Returns the object associated with serialized data.  Will not notice if
     * the type of the object is wrong!
     */
    private T deserialize(byte[] data) {
	@SuppressWarnings("unchecked")
	    T deserialized = (T) SerialUtil.deserialize(data);
	return deserialized;
    }
}
