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

package com.sun.sgs.app;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Represents a reference to a managed object.  Classes that implement
 * <code>ManagedReference</code> must also implement {@link Serializable}.
 * Applications should create instances of this interface using the {@link
 * DataManager#createReference DataManager.createReference} method.  These
 * <code>ManagedReference</code> instances should be used to store references
 * to instances of <code>ManagedObject</code> referred to by other managed
 * objects or by the non-managed objects they refer to. <p>
 *
 * Applications should not use instances of <code>ManagedReference</code> as
 * the values of static fields or in other locations not managed by the
 * <code>DataManager</code>.  There is no guarantee that objects only reachable
 * through these external references will continue to be managed by the
 * <code>DataManager</code>. <p>
 *
 * Some implementations may need to be notified when managed objects and the
 * objects they refer to are modified, while other implementations may be
 * configurable to detect these modifications automatically.  Applications are
 * always permitted to mark objects that have been modified, and doing so may
 * produce performance improvements regardless of whether modifications are
 * detected automatically.
 *
 * @param	<T> the type of the referenced object
 * @see		DataManager#createReference DataManager.createReference
 */
public interface ManagedReference<T> {

    /**
     * Obtains the object associated with this reference.  The object returned
     * will implement {@link ManagedObject} and {@link Serializable}.  For
     * implementations that need to be notified of object modifications,
     * applications should call {@link #getForUpdate getForUpdate} or {@link
     * DataManager#markForUpdate DataManager.markForUpdate} before modifying
     * the returned object or any of the non-managed objects it refers to.
     *
     * @return	the associated object
     * @throws	ObjectNotFoundException if the object associated with this
     *		reference is not found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	#getForUpdate getForUpdate
     * @see	DataManager#markForUpdate DataManager.markForUpdate
     */
    T get();

    /**
     * Obtains the managed object associated with this reference, and notifies
     * the system that the object is going to be modified.  The object returned
     * will implement {@link ManagedObject} and {@link Serializable}.
     *
     * @return	the associated object
     * @throws	ObjectNotFoundException if the object associated with this
     *		reference is not found
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     * @see	DataManager#markForUpdate DataManager.markForUpdate
     */
    T getForUpdate();

    /**
     * Returns a unique identifier for the object associated with this
     * reference.  Two references have equal identifiers if and only if they
     * refer to the same object.
     *
     * @return	a unique identifier for this reference
     */
    BigInteger getId();

    /**
     * Compares the specified object with this reference.  Returns
     * <code>true</code> if the argument is a <code>ManagedReference</code>
     * that refers to the same object as this reference, otherwise
     * <code>false</code>.
     *
     * @param	object the object to be compared with
     * @return	if <code>object</code> refers to the same object
     */
    boolean equals(Object object);

    /**
     * Returns an appropriate hash code value for the object.
     *
     * @return	the hash code for this object
     */
    int hashCode();
}
