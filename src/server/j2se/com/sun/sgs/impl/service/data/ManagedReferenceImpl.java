package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.util.LoggerWrapper;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an implementation of ManagedReference.  Instances of this class are
 * associated with a single transaction.  Within a transaction, instances are
 * canonicalized: only a single instance appears for a given object ID or
 * object.
 */
final class ManagedReferenceImpl<T extends ManagedObject>
    implements ManagedReference<T>, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The logger for this class. */
    private static final LoggerWrapper logger =
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
     * created.  This field is logically final, but is not declared final so
     * that it can be set during deserialization.
     */
    private transient Context context;

    /**
     * The object ID.
     *
     * @serial
     */
    final long oid;

    /**
     * The associated object or null.  Note that managed references cannot
     * refer to null, so this field will only be null if the object has not
     * been fetched yet.
     */
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
	 assert object != null : "Object is null";
	 return context.refs.find(object);
     }

    /**
     * Returns the reference associated with a context and object, creating a
     * NEW reference if none is found.
     */
    static <T extends ManagedObject> ManagedReferenceImpl<T> getReference(
	Context context, T object)
     {
	 assert object != null : "Object is null";
	 ManagedReferenceImpl<T> ref = context.refs.find(object);
	 if (ref == null) {
	     ref = new ManagedReferenceImpl<T>(context, object);
	     context.refs.add(ref);
	 }
	 if (logger.isLoggable(Level.FINEST)) {
	     logger.log(Level.FINEST, "getReference object:{0} returns {1}",
			object, ref);
	 }
	 return ref;
     }

    /**
     * Returns the reference associated with a context and object ID, creating
     * an EMPTY reference if none is found.
     */
    static ManagedReferenceImpl<?> getReference(Context context, long oid) {
	 ManagedReferenceImpl<?> ref = context.refs.find(oid);
	 if (ref == null) {
	     ref = new ManagedReferenceImpl(context, oid);
	     context.refs.add(ref);
	 }
	 if (logger.isLoggable(Level.FINEST)) {
	     logger.log(Level.FINEST, "getReference oid:{0} returns {1}",
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
	    /*
	     * Presumably this object is being marked for update because it
	     * will be modified, so fetch the object now.
	     */
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

    /* -- Implement ManagedReference -- */

    public T get() {
	try {
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
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "get {0} returns {1}", this, object);
	    }
	    return object;
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, "get {0} throws", e, this);
	    throw e;
	}
    }

    public T getForUpdate() {
	try {
	    DataServiceImpl.checkContext(context);
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
	    case FLUSHED:
		throw new TransactionNotActiveException(
		    "No transaction is in progress");
	    case NEW:
	    case MODIFIED:
		break;
	    case REMOVED:
		throw new ObjectNotFoundException("The object is not found");
	    default:
		throw new AssertionError();
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "getForUpdate {0} returns {1}",
			   this, object);
	    }
	    return object;
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINEST, "getForUpdate {0} throws", e, this);
	    throw e;
	}
    }

    /* -- Implement Serializable -- */

    /** Replaces this instance with a canonical instance. */
    private Object readResolve() throws ObjectStreamException {
	try {
	    context = DataServiceImpl.getContextNoJoin();
	    state = State.EMPTY;
	    validate();
	    ManagedReferenceImpl<?> ref = context.refs.find(oid);
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

    public String toString() {
	return "ManagedReferenceImpl[oid:" + oid + ", state:" + state + "]";
    }

    /* -- Other methods -- */

    /**
     * Checks the consistency of the managed references table, throwing an
     * exception if a problem is found.
     */
    static void checkAllState(Context context) {
	logger.log(Level.FINE, "Checking state");
	context.refs.checkAllState();
    }

    /**
     * Checks the fields of this object to make sure they have valid values,
     * throwing an assertion error if a problem is found.
     */
    void checkState() {
	switch (state) {
	case NEW:
	    if (object == null) {
		throw new AssertionError("NEW with no object");
	    } else if (fingerprint != null) {
		throw new AssertionError("NEW with fingerprint");
	    }
	    break;
	case EMPTY:
	case REMOVED:
	case FLUSHED:
	    if (object != null) {
		throw new AssertionError(state + " with object");
	    } else if (fingerprint != null) {
		throw new AssertionError(state + " with fingerprint");
	    }
	    break;
	case NOT_MODIFIED:
	case MODIFIED:
	    if (object == null) {
		throw new AssertionError(state + " with no object");
	    } else if (fingerprint != null) {
		throw new AssertionError(state + " with fingerprint");
	    }
	    break;
	case MAYBE_MODIFIED:
	    if (object == null) {
		throw new AssertionError(
		    "MAYBE_MODIFIED with no object");
	    } else if (fingerprint == null) {
		throw new AssertionError(
		    "MAYBE_MODIFIED with no fingerprint");
	    }
	    break;
	default:
	    throw new AssertionError();
	}
    }

    /** Saves all object modifications to the data store. */
    static void flushAll(Context context) {
	context.refs.flushChanges();
    }

    /**
     * Stores any modifications to the data store, and changes the state to
     * FLUSHED.
     */
    void flush() {
	switch (state) {
	case EMPTY:
	    break;
	case NEW:
	case MODIFIED:
 	    context.store.setObject(
		context.txn, oid, SerialUtil.serialize(object));
	    context.refs.unregisterObject(object);
	    break;
	case MAYBE_MODIFIED:
	    if (!SerialUtil.matchingFingerprint(object, fingerprint)) {
		context.store.setObject(
		    context.txn, oid, SerialUtil.serialize(object));
	    }
	    /* Fall through */
	case NOT_MODIFIED:
	    context.refs.unregisterObject(object);
	    break;
	case FLUSHED:
	    throw new IllegalStateException("Object already flushed");
	case REMOVED:
	    throw new IllegalStateException("Object was removed");
	default:
	    throw new AssertionError();
	}
	object = null;
	fingerprint = null;
	state = State.FLUSHED;
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
	/**
	 * The serialized form doesn't know the type, so the real type check
	 * will need to occur in the caller.
	 */
	@SuppressWarnings("unchecked")
	    T deserialized = (T) SerialUtil.deserialize(data);
	if (deserialized == null) {
	    throw new ObjectIOException(
		"Managed object must not deserialize to null", false);
	}
	return deserialized;
    }
}
