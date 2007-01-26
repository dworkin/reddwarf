package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.service.data.store.DataStore.ObjectData;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.Map;

/**
 * Stores information about managed references within a particular transaction.
 * This class is logically part of the ManagedReferenceImpl class.
 */
final class ReferenceTable {

    /** Maps object IDs to managed references. */
    private final Map<Long, ManagedReferenceImpl> oids =
	new HashMap<Long, ManagedReferenceImpl>();

    /**
     * Maps managed objects to managed references.  The objects are compared by
     * identity, not the equals method.
     */
    private final Map<ManagedObject, ManagedReferenceImpl> objects =
	new IdentityHashMap<ManagedObject, ManagedReferenceImpl>();

    /** Creates an instance of this class. */
    ReferenceTable() { }

    /**
     * Finds the managed reference associated with a managed object, returning
     * null if no reference is found.
     */
    ManagedReferenceImpl find(ManagedObject object) {
	assert object != null : "Object is null";
	return objects.get(object);
    }

    /**
     * Finds the managed reference associated with an object ID, returning null
     * if no reference is found.
     */
    ManagedReferenceImpl find(long oid) {
	assert oid >= 0 : "Object ID is negative";
	return oids.get(oid);
    }

    /** Adds a new managed reference to this table. */
    void add(ManagedReferenceImpl ref) {
	assert !oids.containsKey(ref.oid)
	    : "Found existing reference for oid:" + ref.oid;
	oids.put(ref.oid, ref);
	ManagedObject object = ref.getObject();
	if (object != null) {
	    assert !objects.containsKey(object)
		: "Found existing reference for object with oid:" + ref.oid;
	    objects.put(object, ref);
	}
    }

    /**
     * Updates this table for a reference that has been newly associated with
     * an object.
     */
    void registerObject(ManagedReferenceImpl ref) {
	assert oids.get(ref.oid) == ref
	    : "Found duplicate references for oid: " + ref.oid;
	assert ref.getObject() != null : "Object is null for oid:" + ref.oid;
	assert !objects.containsKey(ref.getObject())
	    : "Found existing reference for object with oid: " + ref.oid;
	objects.put(ref.getObject(), ref);
    }

    /**
     * Updates this table for a reference that is no longer associated with an
     * object.
     */
    void unregisterObject(ManagedObject object) {
	assert objects.containsKey(object) : "Object was not found";
	objects.remove(object);
    }

    /** Removes a managed reference from this table. */
    void remove(ManagedReferenceImpl ref) {
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

    /**
     * Returns an iterator that supplies the object data that needs to be
     * stored to the data store and flushes all references as a side effect.
     */
    Iterator<ObjectData> flushModifiedObjects() {
	return new FlushObjectsIterator(oids);

    }

    /**
     * Supplies object data that needs to be stored to the data store, and
     * flushes all references.
     */
    private static class FlushObjectsIterator implements Iterator<ObjectData> {
	private final Iterator<ManagedReferenceImpl> iter;
	private ObjectData next;
	ObjectDataIter(Set<ManagedReferenceImpl> refs) {
	    this.iter = refs.iterator();
	    next = getNext();
	}
	public boolean hasNext() {
	    return next != null;
	}
	public ObjectData next() {
	    if (!hasNext()) {
		throw new NoSuchElementException();
	    }
	    ObjectData result = next;
	    next = getNext();
	    return result;
	}
	public void remove() {
	    throw new UnsupportedOperationException();
	}
	private ObjectData getNext() {
	    while (iter.hasNext()) {
		ManagedReferenceImpl ref = iter.next();
		byte[] data = ref.flush();
		if (data != null) {
		    return new ObjectData(ref.oid, data);
		}
	    }
	    return null;
	}
    }

    /**
     * Checks the consistency of this table, throwing an assertion error if a
     * problem is found.
     */
    void checkAllState() {
	int objectCount = 0;
	for (Entry<Long, ManagedReferenceImpl> entry : oids.entrySet()) {
	    long oid = entry.getKey();
	    ManagedReferenceImpl ref = entry.getValue();
	    ref.checkState();
	    if (oid != ref.oid) {
		throw new AssertionError(
		    "Wrong oids entry: oid = " + oid + ", ref.oid = " +
		    ref.oid);
	    }
	    Object object = ref.getObject();
	    if (object != null) {
		ManagedReferenceImpl objectsRef = objects.get(object);
		if (objectsRef == null) {
		    throw new AssertionError(
			"Missing objects entry for oid = " + ref.oid);
		} else if (!ref.equals(objectsRef)) {
		    throw new AssertionError(
			"Wrong objects entry for oid = " + ref.oid);
		}
		objectCount++;
	    }
	}
	if (objectCount != objects.size()) {
	    throw new AssertionError(
		"Objects table has wrong size: was " + objects.size() +
		", expected " + objectCount);
	}
    }
}
