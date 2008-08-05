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

import com.sun.sgs.service.Transaction;


/**
 * Used to notify the coordinator of requested access to some
 * object. Methods must be called in the context of an active
 * transaction.
 *
 * @param <T> the type of the object being accessed
 */
public interface AccessNotificationProxy<T> {

    /** The type of access requested. */
    public enum AccessType {
        /** The object is being accessed, but not modified. */
        READ,
        /** The object is being modified. */
        WRITE
    }

    /**
     * Notifies the coordinator that an object has been accessed in the
     * context of the current transaction. This object is shared, and
     * may be a source of contention.
     * <p>
     * The {@code objId} parameter must implement {@code equals()} and
     * {@code hashCode()}. To make the resulting detail provided to the
     * profiler as useful as possible, {@code objId} should have a meaningful
     * toString() method.
     * <p>
     * TODO: in the next phase of this work an exception will be thrown
     * from this method if the object access causes the calling transaction
     * to fail (e.g., due to conention).
     *
     * @param objId the {@code Object} being accessed
     * @param type the {@code AccessType} being requested
     *
     * @throws TransactionNotActiveException if not called in the context
     *                                       of an active transaction
     */
    public void notifyObjectAccess(T objId, AccessType type);

    /**
     * Notifies the coordinator that an object with the provided
     * description has been accessed in the context of the current
     * transaction. This object is shared, and may be a source of
     * contention.
     * <p>
     * The {@code objId} parameter must implement {@code equals()} and
     * {@code hashCode()}. To make the resulting detail provided to the
     * profiler as useful as possible, {@code objId} should have a meaningful
     * toString() method.
     * <p>
     * TODO: in the next phase of this work an exception will be thrown
     * from this method if the object access causes the calling transaction
     * to fail (e.g., due to conention).
     *
     * @param objId the {@code Object} being accessed
     * @param type the {@code AccessType} being requested
     * @param description an arbitrary object that contains a
     *        description of the objId being accessed
     *
     * @throws TransactionNotActiveException if not called in the context
     *                                       of an active transaction
     */
    public void notifyObjectAccess(T objId, AccessType type, 
				   Object description);


    /**
     * Optionally mark the given object with some description that should have
     * a meaningful toString() method. This will be available in the
     * profiling data, and is useful when displaying details about a given
     * accessed object. Note that this may be called before the object is
     * actually accessed, and thefore before {@code notifyObjectAccess}
     * is called for the given {@code objId}.
     *
     * @param objId the {@code Object} to annotate
     * @param annotation an arbitrary object that contains a
     *        description of the objId being accessed
     *
     * @throws TransactionNotActiveException if not called in the context
     *                                       of an active transaction
     */
    public void setObjectDescription(T objId, Object description);

}
