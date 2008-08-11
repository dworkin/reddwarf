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

package com.sun.sgs.profile;

import com.sun.sgs.kernel.AccessedObject;

import java.util.List;


/**
 * An interface accesible through a {@code ProfileReport} that details the
 * collection of object accesses that occurred during the associated
 * transaction. If that transaction failed due to some kind of conflict, then
 * the type of failure and conflicting task is also identified.
 */
public interface AccessedObjectsDetail {

    /** Identifies a known type of conflict. */
    public static enum ConflictType {
        /** Some access resulted in deadlock between transactions. */
        DEADLOCK,
        /**
         * Some requested access was not granted, e.g. due to timeout
         * or a lock being unavailable.
         */
        ACCESS_NOT_GRANTED,
        /** There was conflict, but the type of conflict is unknown. */
        UNKNOWN,
        /** There was no conflict caused by these object accesses. */
        NONE
    }

    /**
     * Returns an arbitrary but unique identifier associated with this
     * set of object accesses. This can be used to track which transaction
     * caused another transaction to fail.
     *
     * @return an identifier for this set of object accesses
     */
    public long getId();

    /**
     * The collection of requested object accesses. The order is the same
     * as the order in which the objects were requested in the transaction.
     *
     * @param a {@code List} of {@code AccessedObject}s
     */
    public List<AccessedObject> getAccessedObjects();

    /**
     * The type of conflict, if any, caused by these object accesses.
     *
     * @return the {@code ConflictType}
     */
    public ConflictType getConflictType();

    /**
     * Returns the identifier of the accessor that caused this access to
     * fail, if known. This can be paired with the result of {@code getId}
     * to match successful and failed access attempts. If there was no
     * failure then this throws an exception.
     *
     * @return identifier for the successful access that caused conflict,
     *         or -1 if there was conflict but the accessor is unknown
     *
     * @throws IllegalStateException if no conflict occurred
     */
    public long getConflictingId();

}
