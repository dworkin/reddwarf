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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.app;

import java.io.DataInput;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * Provides facilities for managing access to shared, persistent objects.
 * Managed objects are objects that implement the {@link ManagedObject} and
 * {@link Serializable} interfaces.  Each managed object is stored separately
 * along with all of the serializable, non-managed objects it refers to.  If a
 * managed object refers to another managed object, it must do so through an
 * instance of {@link ManagedReference}, created by the {@link #createReference
 * createReference} method.  Attempting to store a reference to a managed
 * object using a standard reference rather than an instance of
 * <code>ManagedReference</code> will typically result in an {@link
 * ObjectIOException} being thrown when the current transaction commits. <p>
 *
 * Managed objects that are bound to names, and any managed objects they refer
 * to directly or indirectly, are stored by the <code>DataManager</code>.  It
 * is up to the application to determine when managed objects are no longer
 * needed and to remove them explicitly from the <code>DataManager</code> using
 * the {@link #removeObject removeObject} method. <p>
 *
 * Managed objects are in one of the following states with respect to the data
 * manager: <ul>
 *
 * <li><a name="transient"><b>Transient</b></a> - A managed object that has
 *     been created directly by the application and has not been supplied in a
 *     call to {@link #setBinding setBinding}, {@code createReference}, or
 *     {@link #getObjectId getObjectId}.  Transient objects are not known to
 *     the data manager and are not stored persistently. <p>
 *
 *     Passing a transient object to {@code setBinding}, {@code
 *     createReference}, or {@code getObjectId} creates a new entry for the
 *     object in the data manager.  Calling {@code removeObject} or {@link
 *     #markForUpdate markForUpdate} on a transient object does not change the
 *     state of the object.  If a transient object implements {@link
 *     ManagedObjectRemoval}, though, then calling {@code removeObject} will
 *     cause its {@link ManagedObjectRemoval#removingObject removingObject}
 *     method to be called. <p>
 *
 * <li><a name="persistent"><b>Persistent</b></a> - A managed object that has
 *     been stored in the data manager by calling {@code setBinding}, {@code
 *     createReference}, or {@code getObjectId} in the current transaction, or
 *     else retrieved from the data manager by calling {@link #getBinding
 *     getBinding}, {@link #getBindingForUpdate getBindingForUpdate}, {@link
 *     ManagedReference#get ManagedReference.get}, or {@link
 *     ManagedReference#getForUpdate ManagedReference.getForUpdate}. <p>
 *
 *     Passing a persistent object to {@code setBinding}, {@code
 *     createReference}, {@code markForUpdate}, or {@code getObjectId} does not
 *     create a new entry for that object in the data manager, but rather
 *     reuses the existing one.  Calling {@code removeObject} on a persistent
 *     object removes it from the data manager, meaning it will no longer be
 *     stored persistently, and changes its state to <i>removed</i>.  Note that
 *     a managed object made persistent in the current transaction is
 *     automatically marked for update, so there is no need to call {@code
 *     markForUpdate} for such objects, in particular in their
 *     constructors. <p>
 *
 * <li><a name="removed"><b>Removed</b></a> - A managed object that has been
 *     removed from the data manager by calling {@code removeObject}.  Removed
 *     objects are no longer stored persistently, although the data manager
 *     makes an effort to track which managed objects have been removed. <p>
 *
 *     Only persistent managed objects can become removed; calling {@code
 *     removeObject} on a transient managed object leaves it as a transient
 *     object.  Passing a removed managed object to {@code setBinding}, {@code
 *     removeObject}, {@code markForUpdate}, {@code createReference}, or {@code
 *     getObjectId}, as well as calling {@code ManagedReference.get} or {@code
 *     ManagedReference.getForUpdate} on a reference to the removed object,
 *     will cause a {@link ObjectNotFoundException} to be thrown, if the system
 *     has tracked the object's removed state. <p>
 *
 * <li><a name="stale"><b>Stale</b></a> - A managed object that was persistent
 *     or removed in another, completed transaction.  Stale objects are objects
 *     that have become detached from the data manager, and should not be
 *     visible to applications that avoid using static fields, but may be
 *     encountered under some circumstances.  The data manager implementation
 *     may or may not track stale objects.  If stale objects are not tracked,
 *     they will be considered transient. <p>
 *
 *     Passing a stale object to {@code setBinding}, {@code removeObject},
 *     {@code markForUpdate}, {@code createReference}, or {@code getObjectId},
 *     as well as calling {@code ManagedReference.get} or {@code
 *     ManagedReference.getForUpdate} on a reference to the stale object, will
 *     cause a {@link TransactionNotActiveException} to be thrown, if the
 *     system has tracked the object's stale state. <p>
 *
 * </ul> <p>
 *
 * Because storing a managed object in a name binding or creating a managed
 * reference to it causes the object to be stored in data manager, applications
 * should insure that they remove such objects if they end up not referring to
 * them persistently.  The {@code removeObject} method does not throw
 * exceptions when called on a transient object, so applications should call
 * {@code removeObject} whenever there is an object that may have become
 * persistent and they are sure is no longer used. <p>
 *
 * Some implementations may need to be notified when managed objects and the
 * objects they refer to are modified, while other implementations may be
 * configurable to detect these modifications automatically.  Applications are
 * always permitted to mark objects that have been modified, and doing so may
 * produce performance improvements regardless of whether modifications are
 * detected automatically.
 *
 * @see         AppContext#getDataManager
 * @see		ManagedObject
 * @see		ManagedReference
 * @see		Serializable
 */
public interface DataManager {

    /**
     * Obtains the object bound to a name.  For implementations that need to be
     * notified of object modifications, applications should call {@link
     * #markForUpdate markForUpdate} or {@link ManagedReference#getForUpdate
     * ManagedReference.getForUpdate} before modifying the returned object or
     * any of the non-managed objects it refers to.
     *
     * @param	name the name
     * @return	the object bound to the name
     * @throws	NameNotBoundException if no object is bound to the name
     * @throws	ObjectNotFoundException if the object bound to the name is not
     *		found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	#markForUpdate markForUpdate
     * @see	ManagedReference#getForUpdate ManagedReference.getForUpdate
     * @see	#getBindingForUpdate getBindingForUpdate
     */
    ManagedObject getBinding(String name);

    /**
     * Obtains the object bound to a name, and notifies the system that the
     * object is going to be modified.
     *
     * @param	name the name
     * @return	the object bound to the name
     * @throws	NameNotBoundException if no object is bound to the name
     * @throws	ObjectNotFoundException if the object bound to the name is not
     *		found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    ManagedObject getBindingForUpdate(String name);

    /**
     * Binds an object to a name, replacing any previous binding, and storing
     * the object in the data manager if it is <a
     * href="#transient">transient</a>.  The object must implement {@link
     * ManagedObject}, and both the object and any objects it refers to must
     * implement {@link Serializable}.  Note that this method will throw {@link
     * IllegalArgumentException} if <code>object</code> does not implement
     * <code>Serializable</code>, but is not guaranteed to check that all
     * referred to objects implement <code>Serializable</code>.  Any instances
     * of {@link ManagedObject} that <code>object</code> refers to directly, or
     * indirectly through non-managed objects, need to be referred to through
     * instances of {@link ManagedReference}.
     *
     * @param	name the name
     * @param	object the object
     * @throws	IllegalArgumentException if <code>object</code> does not
     *		implement both {@link ManagedObject} and {@link Serializable}
     * @throws	ObjectNotFoundException if the object has been removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void setBinding(String name, Object object);

    /**
     * Removes the binding for a name.  Note that the object previously bound
     * to the name, if any, is not removed; only the binding between the name
     * and the object is removed.  To remove the object, use the {@link
     * #removeObject removeObject} method.
     *
     * @param	name the name
     * @throws	NameNotBoundException if the name is not bound
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	#removeObject removeObject
     */
    void removeBinding(String name);

    /**
     * Returns the next name after the specified name that has a binding, or
     * <code>null</code> if there are no more bound names.  If
     * <code>name</code> is <code>null</code>, then the search starts at the
     * beginning. <p>
     *
     * The order of the names corresponds to the ordering of the UTF-8 encoding
     * of the names.  To provide flexibility to the implementation, the UTF-8
     * encoding used can be either <em>standard UTF-8</em>, as defined by the
     * IETF in <a href="http://tools.ietf.org/html/rfc3629">RFC 3629</a>, or
     * <em>modified UTF-8</em>, as used by serialization and defined by the
     * {@link DataInput} interface.
     *
     * @param	name the name to search after, or <code>null</code> to start at
     *		the beginning
     * @return	the next name with a binding following <code>name</code>, or
     *		<code>null</code> if there are no more bound names
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    String nextBoundName(String name);

    /**
     * Removes an object from the <code>DataManager</code>, if the object is <a
     * href="#persistent">persistent</a>.  The system will make an effort to
     * flag subsequent references to the removed object through {@link
     * #getBinding getBinding} or {@link ManagedReference} by throwing {@link
     * ObjectNotFoundException}, although this behavior is not guaranteed. <p>
     *
     * If {@code object} implements {@link ManagedObjectRemoval}, even if it is
     * <a href="#transient">transient</a>, then this method first calls the
     * {@link ManagedObjectRemoval#removingObject
     * ManagedObjectRemoval.removingObject} method on the object, to notify it
     * that it is being removed.  If the call to {@code removingObject} throws
     * a {@code RuntimeException}, then this method will throw that exception
     * without removing the object.  A call to {@code removingObject} that
     * causes {@code removeObject} to be called recursively on the same object
     * will result in an {@code IllegalStateException} being thrown.
     *
     * @param	object the object
     * @throws	IllegalArgumentException if {@code object} does not implement
     *		both {@link ManagedObject} and {@link Serializable}
     * @throws	IllegalStateException if {@code object} implements {@code
     *		ManagedObjectRemoval} and {@code removeObject} is called
     *		recursively on the object through a call to {@link
     *		ManagedObjectRemoval#removingObject
     *		ManagedObjectRemoval.removingObject}
     * @throws	ObjectNotFoundException if the object has already been removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @throws	RuntimeException if {@code object} implements {@code
     *		ManagedObjectRemoval} and calling {@link
     *		ManagedObjectRemoval#removingObject
     *		ManagedObjectRemoval.removingObject} on the object throws a
     *		runtime exception
     * @see	ManagedObjectRemoval
     */
    void removeObject(Object object);

    /**
     * Notifies the system that an object is going to be modified, doing
     * nothing if the object is <a href="#transient">transient</a>.
     *
     * @param	object the object
     * @throws	IllegalArgumentException if <code>object</code> does not
     *		implement both {@link ManagedObject} and {@link Serializable}
     * @throws	ObjectNotFoundException if the object has been removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	ManagedReference#getForUpdate ManagedReference.getForUpdate 
     */
    void markForUpdate(Object object);

    /**
     * Creates a managed reference to an object, storing the object in the data
     * manager if it is <a href="#transient">transient</a>.  Applications
     * should use managed references when a managed object refers to another
     * managed object, either directly or indirectly through non-managed
     * objects.
     *
     * @param	<T> the type of the object
     * @param	object the object
     * @return	the managed reference
     * @throws	IllegalArgumentException if <code>object</code> does not
     *		implement both {@link ManagedObject} and {@link Serializable}
     * @throws	ObjectNotFoundException if the object has been removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    <T> ManagedReference<T> createReference(T object);

    /**
     * Returns a unique identifier for the object, storing the object in the
     * data manager if it is <a href="#transient">transient</a>.  The value
     * returned by this method is the same as the one that would be obtained by
     * calling {@link ManagedReference#getId ManagedReference.getId} on a
     * managed reference associated with the object produced by {@link
     * #createReference createReference}.
     *
     * @param	object the object
     * @return	a unique identifier for the object
     * @throws	IllegalArgumentException if <code>object</code> does not
     *		implement both {@link ManagedObject} and {@link Serializable}
     * @throws	ObjectNotFoundException if the object has been removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    BigInteger getObjectId(Object object);
}
