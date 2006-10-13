package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/** Stores information about managed references. */
final class ReferenceTable {

    /** Maps object IDs to managed references. */
    private final Map<Long,
		      ManagedReferenceImpl<? extends ManagedObject>> ids =
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
	assert object != null;
	@SuppressWarnings("unchecked")
	    ManagedReferenceImpl<T> result =
	        (ManagedReferenceImpl<T>) objects.get(object);
	return result;
    }

    /**
     * Finds the managed reference associated with an object ID, returning null
     * if no reference is found.
     */
    ManagedReferenceImpl<? extends ManagedObject> find(long id) {
	assert id >= 0;
	return ids.get(id);
    }

    /** Adds a new managed reference to this table. */
    void add(ManagedReferenceImpl<? extends ManagedObject> ref) {
	Object existing = ids.put(ref.id, ref);
	assert existing == null;
	ManagedObject object = ref.getObject();
	if (object != null) {
	    existing = objects.put(object, ref);
	    assert existing == null;
	}
    }

    /**
     * Updates this table for a reference that has been newly associated with
     * an object.
     */
    void registerObject(ManagedReferenceImpl<? extends ManagedObject> ref) {
	assert ids.get(ref.id) == ref;
	assert ref.getObject() != null;
	Object existing = objects.put(ref.getObject(), ref);
	assert existing == null;
    }

    /** Removes a managed reference from this table. */
    void remove(ManagedReferenceImpl<? extends ManagedObject> ref) {
	Object existing = ids.remove(ref.id);
	assert existing == ref;
	ManagedObject object = ref.getObject();
	if (object != null) {
	    existing = objects.remove(object);
	    assert existing == ref;
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
	int objectCount = 0;
	for (Entry<Long, ManagedReferenceImpl<? extends ManagedObject>> entry :
		 ids.entrySet())
	{
	    long id = entry.getKey();
	    ManagedReferenceImpl<? extends ManagedObject> ref =
		entry.getValue();
	    ref.checkState();
	    if (id != ref.id) {
		throw new IllegalStateException(
		    "Wrong ids entry: id = " + id + ", ref.id = " + ref.id);
	    }
	    Object object = ref.getObject();
	    if (object != null) {
		if (!ref.equals(objects.get(object))) {
		    throw new IllegalStateException(
			"Missing entry in objects for id = " + ref.id +
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
