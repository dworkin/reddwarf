/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
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

import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.impl.service.data.store.ClassInfoNotFoundException;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.util.Int30;
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

/**
 * Manages information about class descriptors used to serialize managed
 * objects.  This class caches information about class descriptors and class
 * IDs that it obtains from the data store.  The cache is filled on demand, and
 * uses soft and weak references, so it can be cleared by the GC as needed.
 */
final class ClassesTable {

    /*
     * TBD: Maybe use futures to avoid simultaneous requests for the same
     * class?  -tjb@sun.com (05/21/2007)
     */

    /** The data store used to store class information. */
    private final DataStore store;

    /*
     * Note that using concurrent maps turned out to be too inefficient to be
     * useful.  Because the maps are likely to quickly include all of the
     * entries needed for steady state operation, supporting fast concurrent
     * reads via a shared read lock seems like a better approach than
     * permitting concurrent modifications.  -tjb@sun.com (05/18/2007)
     */

    /**
     * Maps class IDs to class descriptors.  Use soft references to the
     * descriptors since they are still useful if unreferenced, but can be
     * reconstructed if needed.
     */
    private final Map<Integer, SoftReference<ObjectStreamClass>> classDescMap =
	new HashMap<Integer, SoftReference<ObjectStreamClass>>();

    /** Reference queue for cleared ObjectStreamClass soft references. */
    private final ReferenceQueue<ObjectStreamClass> refQueue =
	new ReferenceQueue<ObjectStreamClass>();

    /**
     * Maps class descriptors to class IDs.  Use weak references to the
     * descriptors since they are compared by identity, and so are not useful
     * if no longer referenced.
     */
    private final Map<ObjectStreamClass, Integer> classIdMap =
	new WeakHashMap<ObjectStreamClass, Integer>();

    /** Lock this lock when accessing classDescMap and classIdMap. */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * A soft reference holder for class descriptor objects stored in
     * classDescMap.
     */
    private static class ClassDescRef
	extends SoftReference<ObjectStreamClass>
    {
	/** The associated class ID key. */
	private final Integer classId;

	/** Creates an instance of this class. */
	ClassDescRef(Integer classId,
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
	    ClassDescRef ref;
	    /*
	     * Reference queues don't provide a way to specify that the queue
	     * contains a particular subclass of reference, so the unchecked
	     * assignment can't be avoided.  -tjb@sun.com (05/18/2007)
	     */
	    while ((ref = (ClassDescRef) (Object) queue.poll()) != null) {
		map.remove(ref.classId);
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
     * @throws	TransactionAbortedException if the data store was consulted and
     *		the transaction was aborted due to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the data store was consulted
     *		and the transaction is not active
     * @throws	IllegalStateException if the data store was consulted and the
     *		operation failed because of a problem with the current
     *		transaction
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
	if (classId > Int30.MAX_VALUE) {
	    throw new IllegalStateException(
		"Allocating more than " + Int30.MAX_VALUE +
		" classes is not supported");
	}
	updateMaps(classId, classDesc);
	return classId;
    }

    /**
     * Updates the maps to refer to the specified class ID and class
     * descriptor.  Returns the descriptor that ends up mapped to the class ID,
     * which may be different from the one passed in if another one was
     * obtained concurrently.
     */
    private ObjectStreamClass updateMaps(Integer classId,
					 ObjectStreamClass classDesc)
    {
	lock.writeLock().lock();
	try {
	    ClassDescRef.processQueue(refQueue, classDescMap);
	    SoftReference<ObjectStreamClass> ref = classDescMap.get(classId);
	    ObjectStreamClass existing = (ref != null) ? ref.get() : null;
	    if (existing == null) {
		classDescMap.put(
		    classId, new ClassDescRef(classId, classDesc, refQueue));
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
     * @throws	TransactionAbortedException if the data store was consulted and
     *		the transaction was aborted due to a lock conflict or timeout
     * @throws	TransactionNotActiveException if the data store was consulted
     *		and the transaction is not active
     * @throws	IllegalStateException if the data store was consulted and the
     *		operation failed because of a problem with the current
     *		transaction
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
	try {
	    return updateMaps(
		classId, getClassDesc(store.getClassInfo(txn, classId)));
	} catch (ClassInfoNotFoundException e) {
	    throw new ObjectIOException(
		"Problem deserializing class descriptor: " + e.getMessage(),
		e, false);
	}
    }

    /**
     * Converts a class descriptor into its serialized form.
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
     * Converts a class descriptor's serialized form into the descriptor.
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
