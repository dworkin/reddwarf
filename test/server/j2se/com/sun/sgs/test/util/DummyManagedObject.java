package com.sun.sgs.test.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/** Implements a simple managed object, for use in testing. */
public class DummyManagedObject implements ManagedObject, Serializable {

    /** The version of the serialized form. */
    private static long serialVersionUID = 1;

    /** The next value of the id field -- used for equality checks. */
    private static AtomicInteger nextId = new AtomicInteger(1);

    /** A unique identifier for this object -- used for equality checks. */
    private final int id;

    /** The data manager for dirtying and obtaining references. */
    private transient final DataManager dataManager;

    /** An arbitrary object. */
    public Object value = null;

    /** A reference to another DummyManagedObject, or null. */
    private ManagedReference next = null;

    /** Creates an instance of this class using the specified data manager. */
    public DummyManagedObject(DataManager dataManager) {
	this.dataManager = dataManager;
	id = nextId.getAndIncrement();
    }

    /**
     * Creates an instance of this class using the specified data manager and
     * specific ID.
     */
    public DummyManagedObject(DataManager dataManager, int id) {
	this.dataManager = dataManager;
	this.id = id;
    }

    /**
     * Sets the value field, calling markForUpdate to note the
     * modification.
     */
    public void setValue(Object value) {
	dataManager.markForUpdate(this);
	this.value = value;
    }

    /**
     * Returns the referenced DummyManagedObject, or null if none is present.
     * The call does not mark the return value for update.
     */ 
    public DummyManagedObject getNext() {
	if (next == null) {
	    return null;
	} else {
	    return next.get(DummyManagedObject.class);
	}
    }

    /**
     * Returns the referenced DummyManagedObject, marking the return value for
     * update by calling getForUpdate on the reference, or null if none is
     * present.
     */ 
    public DummyManagedObject getNextForUpdate() {
	if (next == null) {
	    return null;
	} else {
	    return next.getForUpdate(DummyManagedObject.class);
	}
    }

    /**
     * Sets the referenced DummyManagedObject, calling markForUpdate to note
     * the modification.
     */
    public void setNext(DummyManagedObject next) {
	dataManager.markForUpdate(this);
	this.next = dataManager.createReference(next);
    }

    public boolean equals(Object object) {
	return object instanceof DummyManagedObject &&
	    id == ((DummyManagedObject) object).id;
    }

    public int hashCode() {
	return id;
    }

    public String toString() {
	return "DummyManagedObject[id:" + id +
	    (value != null ? ", value:" + value : "") +
	    ", next:" + next + "]";
    }
}
