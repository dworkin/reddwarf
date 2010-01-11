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
