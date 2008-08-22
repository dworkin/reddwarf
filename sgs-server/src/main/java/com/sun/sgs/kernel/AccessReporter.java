/*
 * Copyright 2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.kernel;

import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.service.Transaction;


/**
 * Used to report to the {@link AccessCoordinator} when access is
 * requested to a shared object. Methods must be called in the context
 * of an active transaction, or provide a transaction in which the
 * context of the request took place.  The latter option is primarily
 * provided for services to report accesses that were only detected
 * during the prepare phase when the transaction is no longer active.
 * <p>
 * For the {@code reportObjectAccess} methods, access should be
 * reported as early as possible. In particular, if actually resolving
 * or retrieving the object could fail, or incur any significant
 * expense, the report should be made first. This is to ensure that
 * access is always noted, and has the chance to abort the transaction
 * before any unneeded processing is done. For instance, in the case
 * of the {@code DataService}, before a name binding is resolved in
 * the {@code getBinding} method, the requested access to that bound
 * object should be reported.
 *
 * @param <T> the type of the identifier used to identify accessed objects
 */
public interface AccessReporter<T> {

    /** The type of access requested. */
    enum AccessType {
        /** The object is being accessed, but not modified. */
        READ,
        /** The object is being modified. */
        WRITE
    }

    /**
     * Reports to the coordinator that object access has been requested in
     * the context of the current transaction. The requested object is shared,
     * and may be the cause of conflict.
     * <p>
     * The {@code objId} parameter must implement {@code equals()} and
     * {@code hashCode()}. To make the resulting detail provided to the
     * profiler as useful as possible, {@code objId} should have a meaningful
     * {@code toString()} method. Other than this the identifier may be any arbitrary
     * instance, including the requested object itself, as long as it
     * uniquely identifies the object across transactions.
     *
     * @param objId an identifier for the object being accessed
     * @param type the {@code AccessType} being requested
     */
    void reportObjectAccess(T objId, AccessType type);

    /**
     * Reports to the coordinator that object access has been
     * requested in the context of the provided transaction. The
     * requested object is shared, and may be the cause of conflict.
     * <p>
     * The {@code objId} parameter must implement {@code equals()} and
     * {@code hashCode()}. To make the resulting detail provided to the
     * profiler as useful as possible, {@code objId} should have a meaningful
     * {@code toString()} method. Other than this the identifier may be any arbitrary
     * instance, including the requested object itself, as long as it
     * uniquely identifies the object across transactions.
     * <p>
     * TODO: in the next phase of this work an exception will be thrown
     * from this method if the object access causes the calling transaction
     * to fail (e.g., due to contention).
     *
     * @param txn the transaction in which the provided {@code objId}
     *        was accessed
     * @param objId an identifier for the object being accessed
     * @param type the {@code AccessType} being requested
     *
     * @throws TransactionNotActiveException if not called in the context
     *                                       of an active transaction
     */
    void reportObjectAccess(Transaction txn, T objId, AccessType type);

    /**
     * Reports to the coordinator that an object access with the provided
     * description has been requested in the context of the current
     * transaction. The requested object is shared, and may be the cause
     * of conflict.
     * <p>
     * The {@code objId} parameter must implement {@code equals()} and
     * {@code hashCode()}. To make the resulting detail provided to the
     * profiler as useful as possible, {@code objId} should have a meaningful
     * toString() method.  Other than this the identifier may be any arbitrary
     * instance, including the requested object itself, as long as it
     * uniquely identifies the object across transactions. See
     * {@code setObjectDescription} for more details about {@code description}.
     * <p>
     * TODO: in the next phase of this work an exception will be thrown
     * from this method if the object access causes the calling transaction
     * to fail (e.g., due to conention).
     *
     * @param objId an identifier for the object being accessed
     * @param type the {@code AccessType} being requested
     * @param description an arbitrary object that contains a
     *                    description of the object being accessed
     *
     * @throws TransactionNotActiveException if not called in the context
     *                                       of an active transaction
     */
    void reportObjectAccess(T objId, AccessType type, 
				   Object description);

    /**
     * Reports to the coordinator that an object access with the provided
     * description has been requested in the context of the provided
     * transaction. The requested object is shared, and may be the cause
     * of conflict.
     * <p>
     * The {@code objId} parameter must implement {@code equals()} and
     * {@code hashCode()}. To make the resulting detail provided to the
     * profiler as useful as possible, {@code objId} should have a meaningful
     * toString() method.  Other than this the identifier may be any arbitrary
     * instance, including the requested object itself, as long as it
     * uniquely identifies the object across transactions. See
     * {@code setObjectDescription} for more details about {@code description}.
     * <p>
     * TODO: in the next phase of this work an exception will be thrown
     * from this method if the object access causes the calling transaction
     * to fail (e.g., due to conention).
     *
     * @param txn the transaction in which the provided {@code objId}
     *        was accessed
     * @param objId an identifier for the object being accessed
     * @param type the {@code AccessType} being requested
     * @param description an arbitrary object that contains a
     *                    description of the object being accessed
     *
     * @throws TransactionNotActiveException if not called in the context
     *                                       of an active transaction
     */
    void reportObjectAccess(Transaction txn, T objId, AccessType type, 
			    Object description);


    /**
     * In the current transaction, associates the given object with
     * some description that should have a meaningful {@code toString}
     * method. This will be available in the profiling data, and is
     * useful when displaying details about a given accessed
     * object. The intent is that an arbitrary description can be
     * included with an object, but that the description is not
     * accessed unless a {@code ProfileListener} finds it useful to do
     * so. At that point the description's {@code toString} method may
     * be called, or the object itself might even be cast to some
     * known type to extract more detail about the accessed object.
     * <p>
     * Note that this may be called before the associated object is
     * actually accessed, and therefore before {@code notifyObjectAccess}
     * is called for the given {@code objId}. Use of this method is
     * optional, and only used to provide additional detail for profiling
     * and debugging.
     * <p>
     * If the provided description is {@code null}, no {@code
     * NullPointerException} is thrown.  Instead, id's current
     * description will be left unchanged.
     *
     * @param objId the identifier for the associated object
     * @param description an arbitrary {@code Object} that contains a
     *        description of the objId being accessed
     *
     * @throws TransactionNotActiveException if not called in the context
     *                                       of an active transaction
     */
    void setObjectDescription(T objId, Object description);

    /**
     * In the provided transaction, associates the given object with
     * some description that should have a meaningful {@code toString}
     * method. This will be available in the profiling data, and is
     * useful when displaying details about a given accessed
     * object. The intent is that an arbitrary description can be
     * included with an object, but that the description is not
     * accessed unless a {@code ProfileListener} finds it useful to do
     * so. At that point the description's {@code toString} method may
     * be called, or the object itself might even be cast to some
     * known type to extract more detail about the accessed object.
     * <p>
     * Note that this may be called before the associated object is
     * actually accessed, and therefore before {@code notifyObjectAccess}
     * is called for the given {@code objId}. Use of this method is
     * optional, and only used to provide additional detail for profiling
     * and debugging.
     * <p>
     * If the provided description is {@code null}, no {@code
     * NullPointerException} is thrown.  Instead, id's current
     * description will be left unchanged.
     *
     * @param txn the transaction in which the provided {@code objId}
     *        was accessed
     * @param objId the identifier for the associated object
     * @param description an arbitrary {@code Object} that contains a
     *        description of the objId being accessed
     *
     * @throws TransactionNotActiveException if not called in the context
     *                                       of an active transaction
     */
    void setObjectDescription(Transaction txn, T objId, 
			      Object description);

}
