/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
