package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.util.LoggerWrapper;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stores information about managed references. */
final class ReferenceTable {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(ReferenceTable.class.getName()));

    /** Maps object IDs to managed references. */
    private final Map<Long,
		      ManagedReferenceImpl<? extends ManagedObject>> oids =
	new HashMap<Long, ManagedReferenceImpl<? extends ManagedObject>>();

    /** Maps managed objects to managed references. */
    private final Map<ManagedObject,
		      ManagedReferenceImpl<? extends ManagedObject>> objects =
	new HashMap<ManagedObject,
		    ManagedReferenceImpl<? extends ManagedObject>>();

    /** Creates an instance of this class. */
    ReferenceTable() { }

    /**
     * Finds the managed reference associated with a managed object, returning
     * null if no reference is found.
     */
    <T extends ManagedObject> ManagedReferenceImpl<T> find(T object) {
	assert object != null : "Object is null";
	@SuppressWarnings("unchecked")
	    ManagedReferenceImpl<T> result =
	        (ManagedReferenceImpl<T>) objects.get(object);
	return result;
    }

    /**
     * Finds the managed reference associated with an object ID, returning null
     * if no reference is found.
     */
    ManagedReferenceImpl<? extends ManagedObject> find(long oid) {
	assert oid >= 0 : "Object ID is negative";
	return oids.get(oid);
    }

    /** Adds a new managed reference to this table. */
    void add(ManagedReferenceImpl<? extends ManagedObject> ref) {
	Object existing = oids.put(ref.oid, ref);
	assert existing == null
	    : "Found existing reference for oid:" + ref.oid;
	ManagedObject object = ref.getObject();
	if (object != null) {
	    existing = objects.put(object, ref);
	    assert existing == null
		: "Found existing reference for object with oid:" + ref.oid;
	}
    }

    /**
     * Updates this table for a reference that has been newly associated with
     * an object.
     */
    void registerObject(ManagedReferenceImpl<? extends ManagedObject> ref) {
	assert oids.get(ref.oid) == ref
	    : "Found duplicate references for oid: " + ref.oid;
	assert ref.getObject() != null : "Object is null for oid:" + ref.oid;
	Object existing = objects.put(ref.getObject(), ref);
	assert existing == null
	    : "Found existing reference for object with oid: " + ref.oid;
    }

    /** Removes a managed reference from this table. */
    void remove(ManagedReferenceImpl<? extends ManagedObject> ref) {
	Object existing = oids.remove(ref.oid);
	assert existing == ref
	    : "Found duplicate reference for oid:" + ref.oid;
	ManagedObject object = ref.getObject();
	if (object != null) {
	    existing = objects.remove(object);
	    assert existing == ref
		: "Found duplicate reference for oid:" + ref.oid;
	}
    }

    /** Obtains the managed references in this table. */
    Collection<ManagedReferenceImpl<? extends ManagedObject>> getReferences() {
	return Collections.unmodifiableCollection(objects.values());
    }

    /**
     * Checks the consistency of this table, throwing an exception if a problem
     * is found.
     */
    void checkState() {
	logger.log(Level.FINE, "Checking state");
	int objectCount = 0;
	for (Entry<Long, ManagedReferenceImpl<? extends ManagedObject>> entry :
		 oids.entrySet())
	{
	    long oid = entry.getKey();
	    ManagedReferenceImpl<? extends ManagedObject> ref =
		entry.getValue();
	    ref.checkState();
	    if (ref.isRemoved()) {
		throw new IllegalStateException(
		    "Found removed reference: " + ref);
	    }
	    if (oid != ref.oid) {
		throw new IllegalStateException(
		    "Wrong oids entry: oid = " + oid + ", ref.oid = " +
		    ref.oid);
	    }
	    Object object = ref.getObject();
	    if (object != null) {
		if (!ref.equals(objects.get(object))) {
		    throw new IllegalStateException(
			"Missing entry in objects for oid = " + ref.oid +
			", object = " + object);
		}
		objectCount++;
	    }
	}
	if (objectCount != objects.size()) {
	    throw new IllegalStateException(
		"Objects table has wrong size: was " + objects.size() +
		", expected " + objectCount);
	}
    }
}
