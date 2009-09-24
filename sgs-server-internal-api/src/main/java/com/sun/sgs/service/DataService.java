/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.service;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionException;
import java.io.DataInput;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * Provides facilities for services to manage access to shared, persistent
 * objects.  In addition to the methods provided by {@link DataManager}, this
 * interface includes methods for an additional set of service bindings.  These
 * service bindings are intended to by used by services to support their needs
 * to store objects persistently.  The objects associated with service bindings
 * are independent of the objects bound through methods defined on
 * <code>DataManager</code>.  Services should insure that they use unique names
 * for service bindings by using their class or package name as the prefix for
 * the name.
 */
public interface DataService extends DataManager, Service {

    /**
     * Returns the node ID for the local node.  The node ID for a node remains
     * fixed for the lifetime of the node (i.e., until it fails). The return
     * value may be passed to {@link WatchdogService#getNode
     * WatchdogService.getNode} to obtain the {@link Node} object for the local
     * node. <p>
     *
     * This method may be invoked any time after this service is initialized,
     * whether or not the calling context is inside or outside of a
     * transaction.
     *
     * @return	the node ID for the local node
     */
    long getLocalNodeId();

    /**
     * Obtains the object associated with the service binding of a name.
     * Callers need to notify the system before modifying the object or any of
     * the non-managed objects it refers to by calling {@link #markForUpdate
     * markForUpdate} or {@link ManagedReference#getForUpdate
     * ManagedReference.getForUpdate} before making the modifications.
     *
     * @param	name the name
     * @return	the object associated with the service binding of the name
     * @throws	NameNotBoundException if no object is bound to the name
     * @throws	ObjectNotFoundException if the object bound to the name is not
     *		found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	#getServiceBindingForUpdate getServiceBindingForUpdate
     */
    ManagedObject getServiceBinding(String name);

    /**
     * Obtains the object associated with the service binding of a name, and
     * notifies the system that the object is going to be modified.
     *
     * @param	name the name
     * @return	the object associated with the service binding of the name
     * @throws	NameNotBoundException if no object is bound to the name
     * @throws	ObjectNotFoundException if the object bound to the name is not
     *		found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    ManagedObject getServiceBindingForUpdate(String name);

    /**
     * Specifies an object for the service binding of a name, replacing any
     * previous binding.  The object must implement {@link ManagedObject}, and
     * both the object and any objects it refers to must implement {@link
     * Serializable}.  Note that this method will throw {@link
     * IllegalArgumentException} if <code>object</code> does not implement
     * <code>Serializable</code>, but is not guaranteed to check that all
     * referred to objects implement <code>Serializable</code>.  Any instances
     * of {@link ManagedObject} that <code>object</code> refers to directly, or
     * indirectly through non-managed objects, need to be referred to through
     * instances of {@link ManagedReference}.
     *
     * @param	name the name
     * @param	object the object associated with the service binding of the
     *		name
     * @throws	IllegalArgumentException if <code>object</code> does not
     *		implement both {@link ManagedObject} and {@link Serializable}
     * @throws	ObjectNotFoundException if the object has been removed
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void setServiceBinding(String name, Object object);

    /**
     * Removes the service binding for a name.  Note that the object previously
     * bound to the name, if any, is not removed; only the binding between the
     * name and the object is removed.  To remove the object, use the {@link
     * #removeObject removeObject} method.
     *
     * @param	name the name
     * @throws	NameNotBoundException if the name is not bound
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	#removeObject removeObject
     */
    void removeServiceBinding(String name);

    /**
     * Returns the next name after the specified name that has a service
     * binding, or <code>null</code> if there are no more bound names.  If
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
     * @return	the next name with a service binding following
     *		<code>name</code>, or <code>null</code> if there are no more
     *		bound names
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    String nextServiceBoundName(String name);

    /**
     * Creates a managed reference for the object with the specified
     * identifier, which should have been obtained from a call to {@link
     * ManagedReference#getId ManagedReference.getId}.  Callers should make
     * sure that the associated object is reachable from an existing name
     * binding.  This method does not check to see whether the associated
     * object has been removed.
     *
     * @param	id the identifier
     * @return	the managed reference
     * @throws	IllegalArgumentException if the implementation is able to
     *		determine that {@code id} was not returned by a call to {@code
     *		ManagedReference.getId}
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    ManagedReference<?> createReferenceForId(BigInteger id);

    /**
     * Returns a unique identifier for the next object after the object with
     * the specified identifier, or {@code null} if there are no more objects.
     * If {@code objectId} is {@code null}, then returns the identifier of the
     * first object.  This method will not return identifiers for objects that
     * have already been removed, and may not include identifiers for newly
     * created objects.  It is not an error for the object associated with
     * {@code objectId} to have already been removed. <p>
     *
     * The object identifiers accepted and returned by this method are the same
     * as those returned by the {@link ManagedReference#getId
     * ManagedReference.getId} method. <p>
     *
     * Callers should not assume that objects associated with the identifiers
     * returned by this method, but which cannot be reached by traversing
     * object field references starting with an object associated with a name
     * binding, will continue to be retained by the data service.
     *
     * @param	objectId the identifier of the object to search after, or
     *		{@code null} to request the first object
     * @return	the identifier of the next object following the object with
     *		identifier {@code objectId}, or {@code null} if there are no
     *		more objects
     * @throws	IllegalArgumentException if the implementation can determine
     *		that {@code objectId} is not a valid object identifier
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    BigInteger nextObjectId(BigInteger objectId);
}
