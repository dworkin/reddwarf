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

package com.sun.sgs.profile;

import com.sun.sgs.kernel.AccessedObject;

import java.util.List;


/**
 * An interface accessible through a {@code ProfileReport} that details the
 * collection of object accesses that occurred during the associated
 * transaction. If that transaction failed due to some kind of conflict, then
 * the type of failure and conflicting task is also identified.
 */
public interface AccessedObjectsDetail {

    /** Identifies a known type of conflict. */
    static enum ConflictType {
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
     * The collection of requested object accesses. The order is the same
     * as the order in which the objects were requested in the transaction.
     *
     * @return a {@code List} of {@code AccessedObject}s
     */
    List<AccessedObject> getAccessedObjects();

    /**
     * The type of conflict, if any, caused by these object accesses.
     *
     * @return the {@code ConflictType}
     */
    ConflictType getConflictType();

    /**
     * Returns the identifier of the transaction that caused this access to
     * fail, if any and if known.
     *
     * @return identifier for the successful transaction that caused conflict,
     *         or {@code null} if there was no conflict or the accessor
     *         is unknown
     */
    byte [] getConflictingId();

}
