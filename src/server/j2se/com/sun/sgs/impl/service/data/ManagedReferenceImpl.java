package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.util.LoggerWrapper;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * States:
 * Transient -- no reference in tables
 * New -- reference in tables, no data, no fingerprint, object
 * Empty -- reference in ID table only, no data, no fingerprint, no object
 * Read -- reference in tables, data, fingerprint, not modified, not flushed
 * Modified -- reference in tables, data fingerprint, modified, not flushed
 * Flushed -- reference in tables, data fingerprint, not modified, flushed
 */
final class ManagedReferenceImpl<T extends ManagedObject>
    implements ManagedReference<T>, Serializable
{
    private final static LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ManagedReferenceImpl.class.getName()));

    private enum State {
	NEW, EMPTY, NOT_MODIFIED, MAYBE_MODIFIED, MODIFIED, FLUSHED, REMOVED
    };

    private final Context context;
    final long id;
    private T object;
    private byte[] fingerprint;
    private State state;

    static <T extends ManagedObject> ManagedReferenceImpl<T> findReference(
	Context context, T object)
     {
	 return context.refs.find(object);
     }

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

    static ManagedReferenceImpl<? extends ManagedObject> getReference(
	Context context, long id)
     {
	 ManagedReferenceImpl<? extends ManagedObject> ref =
	     context.refs.find(id);
	 if (ref == null) {
	     ref = new ManagedReferenceImpl<ManagedObject>(context, id);
	     context.refs.add(ref);
	 }
	 if (logger.isLoggable(Level.FINER)) {
	     logger.log(Level.FINER, "getReference id:{0} returns {1}",
			id, ref);
	 }
	 return ref;
     }

    private ManagedReferenceImpl(Context context, T object) {
	this.context = context;
	id = context.store.createObject(context.txn);
	this.object = object;
	state = State.NEW;
	validate();
    }

    private ManagedReferenceImpl(Context context, long id) {
	this.context = context;
	this.id = id;
	state = State.EMPTY;
	validate();
    }

    private T deserialize(byte[] data) {
	try {
	    @SuppressWarnings("unchecked")
		T deserialized = (T) SerialUtil.deserialize(data);
	    return deserialized;
	} catch (ClassCastException e) {
	    throw new ObjectIOException(
		"Problem deserializing object: " + e.getMessage(), e, false);
	}
    }

    /** Validate field values. */
    private void validate() {
	if (context == null) {
	    throw new NullPointerException("The context must not be null");
	}
	if (id < 0) {
	    throw new IllegalArgumentException("The id must not be negative");
	}
    }

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
	}
    }

    void removeObject() {
	switch (state) {
	case EMPTY:
	case NOT_MODIFIED:
	case MAYBE_MODIFIED:
	case MODIFIED:
	    context.store.removeObject(context.txn, id);
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
	}
    }

    void markForUpdate() {
	switch (state) {
	case EMPTY:
	    context.store.markForUpdate(context.txn, id);
	    break;
	case MAYBE_MODIFIED:
	    fingerprint = null;
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
	}
    }

    void flush() {
	if ((state == State.FLUSHED) || (state == State.REMOVED)) {
	    return;
	}
	boolean modified;
	if (state == State.NEW || state == State.MODIFIED) {
	    modified = true;
	} else if (state == State.MAYBE_MODIFIED) {
	    modified = !SerialUtil.matchingFingerprint(object, fingerprint);
	} else {
	    modified = false;
	}
	if (modified) {
 	    context.store.setObject(
		context.txn, id, SerialUtil.serialize(object));
	}
	object = null;
	fingerprint = null;
	state = State.FLUSHED;
    }

    boolean isNew() {
	return state == State.NEW;
    }

    T getObject() {
	return object;
    }

    /* -- Implement ManagedReference<T> -- */

    public T get() {
	DataServiceImpl.checkContext(context);
	switch (state) {
	case EMPTY:
	    object = deserialize(
		context.store.getObject(context.txn, id, false));
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
	}
	return object;
    }

    public T getForUpdate() {
	DataServiceImpl.checkContext(context);
	switch (state) {
	case EMPTY:
	    object = deserialize(
		context.store.getObject(context.txn, id, true));
	    fingerprint = SerialUtil.fingerprint(object);
	    state = State.MODIFIED;
	    break;
	case NOT_MODIFIED:
	case MAYBE_MODIFIED:
	    context.store.markForUpdate(context.txn, id);
	    state = State.MODIFIED;
	    break;
	case FLUSHED:
	    throw new IllegalStateException("Update flushed");
	default:
	    break;
	}
	return object;
    }

    /* -- Implement Serializable -- */

    private Object writeReplace() throws ObjectStreamException {
	return new ManagedReferenceData(id);
    }

    /* -- Object methods -- */

    public boolean equals(Object object) {
	return this == object;
    }

    public int hashCode() {
	return (int) (id >>> 32) ^ (int) id;
    }

    public String toString() {
	return "ManagedReferenceImpl[id:" + id + ",state:" + state + "]";
    }
}
