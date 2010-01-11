/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.impl.util.Int30;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import com.sun.sgs.service.store.DataStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
     * Maps class IDs to class descriptors and associated information.  Use
     * soft references to the descriptors since they are still useful if
     * unreferenced, but can be reconstructed if needed.
     */
    private final Map<Integer, ClassDescInfo> classDescMap =
	new HashMap<Integer, ClassDescInfo>();

    /** Reference queue for cleared ObjectStreamClass soft references. */
    private final ReferenceQueue<ObjectStreamClass> refQueue =
	new ReferenceQueue<ObjectStreamClass>();

    /**
     * Maps class descriptors to class descriptors and associated information,
     * including class IDs.  Use weak references to the descriptors since they
     * are compared by identity, and so are not useful if no longer referenced.
     */
    private final Map<ObjectStreamClass, ClassDescInfo> classIdMap =
	new WeakHashMap<ObjectStreamClass, ClassDescInfo>();

    /** Lock this lock when accessing classDescMap and classIdMap. */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * A soft reference holder for class descriptor objects, and associated
     * information, stored in classDescMap.
     */
    private static class ClassDescInfo
	extends SoftReference<ObjectStreamClass>
    {
	/** The associated class ID key. */
	final int classId;

	/**
	 * The name of the class, so that we can identify the class even if the
	 * reference to the associated class descriptor has been cleared.
	 */
	private final String className;

	/**
	 * Whether the class is a ManagedObject class with a writeReplace
	 * method.
	 */
	private final boolean hasWriteReplace;

	/**
	 * Whether the class is a ManagedObject class with a readResolve
	 * method.
	 */
	private final boolean hasReadResolve;

	/**
	 * The name of the first non-serializable superclass if it lacks an
	 * accessible no-argument constructor, or {@code null} if there is no
	 * such class.  Note that it is illegal to deserialize an instance of a
	 * class with such a superclass, but serialization does not enforce
	 * that restriction.
	 */
	private final String missingConstructorSuperclass;

	/** Creates an instance of this class. */
	ClassDescInfo(int classId,
		      ObjectStreamClass classDesc,
		      ReferenceQueue<ObjectStreamClass> queue)
	{
	    super(classDesc, queue);
	    this.classId = classId;
	    Class<?> cl = classDesc.forClass();
	    className = cl.getName();
	    boolean isManagedObject = ManagedObject.class.isAssignableFrom(cl);
	    hasWriteReplace =
		isManagedObject && hasSerializationMethod(cl, "writeReplace");
	    hasReadResolve =
		isManagedObject && hasSerializationMethod(cl, "readResolve");
	    missingConstructorSuperclass =
		computeMissingConstructorSuperclass(cl);
	}

	/**
	 * Removes entries from the map that have been queued after being
	 * garbage collected.
	 */
	static void processQueue(ReferenceQueue<ObjectStreamClass> queue,
				 Map<Integer, ?> map)
	{
	    ClassDescInfo ref;
	    /*
	     * Reference queues don't provide a way to specify that the queue
	     * contains a particular subclass of reference, so the unchecked
	     * assignment can't be avoided.  -tjb@sun.com (05/18/2007)
	     */
	    while ((ref = (ClassDescInfo) (Object) queue.poll()) != null) {
		map.remove(ref.classId);
	    }
	}

	/** Checks if the class can be instantiated. */
	void checkInstantiable() throws IOException {
	    if (hasWriteReplace) {
		throw new IOException(
		    "Managed objects must not define a Serialization " +
		    "writeReplace method: " + className);
	    } else if (hasReadResolve) {
		throw new IOException(
		    "Managed objects must not define a Serialization " +
		    "readResolve method: " + className);
	    } else if (missingConstructorSuperclass != null) {
		throw new IOException(
		    "Class " + className + " has a superclass without an" +
		    " accessible no-argument constructor: " +
		    missingConstructorSuperclass);
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
	    public void checkInstantiable(ObjectStreamClass classDesc)
		throws IOException
	    {
		getClassDescInfo(txn, classDesc).checkInstantiable();
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
    int getClassId(Transaction txn, ObjectStreamClass classDesc) {
	return getClassDescInfo(txn, classDesc).classId;
    }

    /**
     * Returns the information associated with a class descriptor.
     * @param	txn the transaction under which the operation should take place
     * @param	classDesc the class descriptor
     * @return	the information about the class descriptor
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
    private ClassDescInfo getClassDescInfo(
	Transaction txn, ObjectStreamClass classDesc)
    {
	lock.readLock().lock();
	try {
	    ClassDescInfo info = classIdMap.get(classDesc);
	    if (info != null) {
		return info;
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
	return updateMaps(classId, classDesc).classDescInfo;
    }

    /**
     * Updates the maps to refer to the specified class ID and class
     * descriptor.  Returns the descriptor, and the associated information,
     * that ends up mapped to the class ID.  Note that the descriptor returned
     * may be different from the one passed in if another one was obtained
     * concurrently.
     */
    private UpdateMapsResult updateMaps(Integer classId,
					ObjectStreamClass classDesc)
    {
	lock.writeLock().lock();
	try {
	    ClassDescInfo.processQueue(refQueue, classDescMap);
	    ClassDescInfo info = classDescMap.get(classId);
	    ObjectStreamClass existing = (info != null) ? info.get() : null;
	    if (existing == null) {
		info = new ClassDescInfo(classId, classDesc, refQueue);
		classDescMap.put(classId, info);
	    }
	    if (!classIdMap.containsKey(classDesc)) {
		classIdMap.put(classDesc, info);
	    }
	    return new UpdateMapsResult(
		(existing != null) ? existing :	classDesc, info);
	} finally {
	    lock.writeLock().unlock();
	}
    }

    /**
     * Stores the ObjectStreamClass and ClassDescInfo returned by a call to
     * updateMaps.  The return value needs to contain both to insure that it
     * maintains a hard reference to the ObjectStreamClass.
     */
    private static class UpdateMapsResult {
	final ObjectStreamClass classDesc;
	final ClassDescInfo classDescInfo;
	UpdateMapsResult(ObjectStreamClass classDesc,
			 ClassDescInfo classDescInfo)
	{
	    this.classDesc = classDesc;
	    this.classDescInfo = classDescInfo;
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
	    UpdateMapsResult result = updateMaps(
		classId, getClassDesc(store.getClassInfo(txn, classId)));
	    return result.classDesc;
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
	    ObjectStreamClass classDesc = (ObjectStreamClass) in.readObject();
	    return classDesc;
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

    /**
     * Returns whether the class defines an inherited method used by
     * Serialization.
     */
    private static boolean hasSerializationMethod(
	Class<?> forClass, String methodName)
    {
	Method method = null;
	Class<?> cl = forClass;
	while (cl != null) {
	    try {
		method = cl.getDeclaredMethod(methodName);
		break;
	    } catch (NoSuchMethodException e) {
		cl = cl.getSuperclass();
	    }
	}
	if (method == null || method.getReturnType() != Object.class) {
	    return false;
	}
	int mods = method.getModifiers();
	if (Modifier.isStatic(mods) || Modifier.isAbstract(mods)) {
	    return false;
	} else if (Modifier.isPublic(mods) || Modifier.isProtected(mods)) {
	    return true;
	} else if (Modifier.isPrivate(mods)) {
	    return forClass == cl;
	} else {
	    return samePackage(forClass, cl);
	}
    }

    /** Checks if the two classes are in the same package. */
    private static boolean samePackage(Class<?> c1, Class<?> c2) {
	return c1.getClassLoader() == c2.getClassLoader() &&
	    getPackageName(c1).equals(getPackageName(c2));
    }

    /** Returns the package name of the class. */
    private static String getPackageName(Class<?> cl) {
	String name = cl.getName();
	int pos = name.lastIndexOf('[');
	if (pos >= 0) {
	    name = name.substring(pos + 2);
	}
	pos = name.lastIndexOf('.');
	return (pos < 0) ? "" : name.substring(0, pos);
    }

    /**
     * Returns the name of the first non-serializable superclass if it lacks an
     * accessible no-arguments constructor, else null.
     */
    private static String computeMissingConstructorSuperclass(Class<?> cl) {
	assert cl != null;
	Class<?> instantiatedClass = cl;
	while (Serializable.class.isAssignableFrom(cl)) {
	    cl = cl.getSuperclass();
	    if (cl == null) {
		return null;
	    }
	}
	try {
	    Constructor constructor = cl.getDeclaredConstructor();
	    int modifiers = constructor.getModifiers();
	    if (Modifier.isPublic(modifiers) ||
		Modifier.isProtected(modifiers) ||
		(!Modifier.isPrivate(modifiers) &&
		 samePackage(cl, instantiatedClass)))
	    {
		return null;
	    } else {
		return cl.getName();
	    }
	} catch (NoSuchMethodException e) {
	    return cl.getName();
	}
    }	    
}
