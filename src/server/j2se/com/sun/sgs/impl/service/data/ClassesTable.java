/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.impl.service.data.store.ClassInfoNotFoundException;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.service.Transaction;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Manages information about serialization class descriptors. */
final class ClassesTable {

    /*
     * TBD: Maybe use futures to have only one simultaneous request for a
     * particular class.  -tjb@sun.com (05/18/2007)
     */

    /** The data store used to store class information. */
    private final DataStore store;

    /** Maps class IDs to class descriptors. */
    private final Map<Integer, SoftReference<ObjectStreamClass>> classDescMap =
	new HashMap<Integer, SoftReference<ObjectStreamClass>>();

    /** Reference queue for cleared ObjectStreamClass soft references. */
    private final ReferenceQueue<ObjectStreamClass> refQueue =
	new ReferenceQueue<ObjectStreamClass>();

    /** Maps class descriptors to class IDs. */
    private final Map<ObjectStreamClass, Integer> classIdMap =
	new WeakHashMap<ObjectStreamClass, Integer>();

    /** Lock this lock when accessing classDescMap and classIdMap. */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** A soft reference holder for ObjectStreamClass objects. */
    private static class SoftValue extends SoftReference<ObjectStreamClass> {

	/** The associated class ID key. */
	private final Integer classId;

	/** Creates an instance of this class. */
	SoftValue(Integer classId,
		  ObjectStreamClass classDesc,
		  ReferenceQueue<ObjectStreamClass> queue)
	{
	    super(classDesc, queue);
	    this.classId = classId;
	}

	/**
	 * Removes entries from the map that have been queued after being
	 * garbage collected.
	 */
	static void processQueue(ReferenceQueue<ObjectStreamClass> queue,
				 Map<Integer, ?> map)
	{
	    SoftValue sv;
	    /*
	     * Reference queues don't provide a way to specify that the queue
	     * contains a particular subclass of reference, so the unchecked
	     * assignment can't be avoided.  -tjb@sun.com (05/18/2007)
	     */
	    while ((sv = (SoftValue) (Object) queue.poll()) != null) {
		map.remove(sv.classId);
	    }
	}
    }

    /** Creates an instance with the specified data store. */
    ClassesTable(DataStore store) {
	this.store = store;
    }

    /**
     * Returns an implementation of ClassSerialization that uses this table to
     * lookup class descriptors, uses the specified transaction when making
     * requests of the data store, and uses Int30 to represent class IDs.
     */
    ClassSerialization createClassSerialization(final Transaction txn) {
	return new ClassSerialization() {
	    public void writeClassDescriptor(ObjectStreamClass classDesc,
					     ObjectOutputStream out)
		throws IOException
	    {
		Int30.write(getClassId(txn, classDesc), out);
	    }
	    public ObjectStreamClass readClassDescriptor(ObjectInputStream in)
		throws ClassNotFoundException, IOException
	    {
		return getClassDesc(txn, Int30.read(in));
	    }
	};
    }

    /**
     * Returns the class ID associated with a class descriptor.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	classDesc the class descriptor
     * @return	the class ID
     * @throws	ObjectIOException if a problem occurs serializing the class
     *		descriptor
     */
    private int getClassId(Transaction txn, ObjectStreamClass classDesc) {
	lock.readLock().lock();
	try {
	    Integer classId = classIdMap.get(classDesc);
	    if (classId != null) {
		return classId;
	    }
	} finally {
	    lock.readLock().unlock();
	}
	int classId = store.getClassId(txn, getClassInfo(classDesc));
	updateMaps(classId, classDesc);
	return classId;
    }

    /**
     * Updates the maps to refer to the specified class ID and class
     * descriptor.  Returns the descriptor that ends up mapped to the class ID,
     * which may be different from the one passed in.
     */
    private ObjectStreamClass updateMaps(Integer classId,
					 ObjectStreamClass classDesc)
    {
	lock.writeLock().lock();
	try {
	    SoftValue.processQueue(refQueue, classDescMap);
	    SoftReference<ObjectStreamClass> ref = classDescMap.get(classId);
	    ObjectStreamClass existing = (ref != null) ? ref.get() : null;
	    if (existing == null) {
		classDescMap.put(
		    classId, new SoftValue(classId, classDesc, refQueue));
	    }
	    if (!classIdMap.containsKey(classDesc)) {
		classIdMap.put(classDesc, classId);
	    }
	    return (existing != null) ? existing : classDesc;
	} finally {
	    lock.writeLock().unlock();
	}
    }

    /**
     * Returns the class descriptor associated with a class ID.
     *
     * @param	txn the transaction under which the operation should take place
     * @param	classId the class ID
     * @return	the class descriptor
     * @throws	ObjectIOException if a problem occurs deserializing the class
     *		descriptor, including if the class ID is not found
     */
    private ObjectStreamClass getClassDesc(Transaction txn, int classId) {
	lock.readLock().lock();
	try {
	    SoftReference<ObjectStreamClass> ref = classDescMap.get(classId);
	    if (ref != null) {
		ObjectStreamClass classDesc = ref.get();
		if (classDesc != null) {
		    return classDesc;
		}
	    }
	} finally {
	    lock.readLock().unlock();
	}
	ObjectStreamClass classDesc;
	try {
	    classDesc = getClassDesc(store.getClassInfo(txn, classId));
	} catch (ClassInfoNotFoundException e) {
	    throw new ObjectIOException(
		"Problem deserializing class descriptor: " + e.getMessage(),
		e, false);
	}
	return updateMaps(classId, classDesc);
    }

    /**
     * Convert a class descriptor into a byte array.
     *
     * @param	classDesc the class descriptor
     * @return	the class information
     * @throws	ObjectIOException if a problem occurs serializing the class
     *		descriptor
     */
    private static byte[] getClassInfo(ObjectStreamClass classDesc) {
	ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
	ObjectOutputStream objectOut = null;
	try {
	    objectOut = new ObjectOutputStream(byteOut);
	    objectOut.writeObject(classDesc);
	    objectOut.flush();
	    return byteOut.toByteArray();
	} catch (IOException e) {
	    throw new ObjectIOException(
		"Problem serializing class descriptor: " + e.getMessage(),
		e, false);
	} finally {
	    if (objectOut != null) {
		try {
		    objectOut.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * Converts byte array information for a class into a class descriptor.
     *
     * @param	classInfo the class information
     * @return	the class descriptor
     * @throws	ObjectIOException if a problem occurs deserializing the class
     *		descriptor
     */
    private static ObjectStreamClass getClassDesc(byte[] classInfo) {
	ObjectInputStream in = null;
	Exception exception;
	try {
	    in = new ObjectInputStream(new ByteArrayInputStream(classInfo));
	    return (ObjectStreamClass) in.readObject();
	} catch (ClassNotFoundException e) {
	    exception = e;
	} catch (IOException e) {
	    exception = e;
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException e) {
		}
	    }
	}
	throw new ObjectIOException(
	    "Problem obtaining class descriptor: " + exception.getMessage(),
	    exception, false);
    }
}
