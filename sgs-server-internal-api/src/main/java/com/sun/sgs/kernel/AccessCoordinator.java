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

package com.sun.sgs.kernel;

import com.sun.sgs.service.Transaction;


/**
 * A central point for tracking all shared objects accessed in the context
 * of a transaction, and managing any conflict these accesses may cause.
 * In this case, "shared object" means any object that is accessible within
 * any transaction on any node. An example would be a {@code ManagedObject}
 * as provided by the {@code DataService}.
 * <p>
 * Each {@code sourceName} determines a unique namespace of objects.  Any
 * {@link AccessReporter} registered with the same {@code sourceName}
 * through a given {@code AccessCoordinator} instance, independent of the
 * {@code objectIdType}, is considered to report accesses to the same set
 * of shared objects. Conflicting accesses to a single object can be reported
 * to different {@code AccessReporter}s if those reporters were obtained from
 * the same access coordinator using the same source name. Object accesses
 * reported to {@code AccessReporter}s obtained from registering access
 * sources with different names will never conflict.
 */
public interface AccessCoordinator {

    /**
     * Register as a provider of shared objects, and therefore a possible
     * source of conflict between transactions. This should be used
     * by any component that is providing access to shared objects (e.g.,
     * the {@code DataService} which provides access to
     * {@code ManagedObject}s and name bindings). This ensures that
     * conflict is managed for access to the reported objects, and
     * that a report of all accesses is available through profiling.
     *
     * @param sourceName the name of the source of objects which may
     *                   cause conflict on access
     * @param objectIdType the type of the identifier that will be used
     *                     to identify accessed objects
     * @param <T> the type of the id object used to report accesses
     *
     * @return an {@code AccessReporter} used to notify the system
     *         of access to shared objects
     */
    <T> AccessReporter<T> registerAccessSource(String sourceName,
                                               Class<T> objectIdType);

    /**
     * Find out what transaction, if still active, caused the given
     * transaction to fail due to conflict. This is particularly useful
     * if you want to wait for that active transaction to finish, e.g.
     * for re-trying the failed transaction.
     *
     * @param txn a {@code Transaction} that failed due to conflict
     *
     * @return the active {@code Transaction} that caused the provided
     *         {@code Transaction} to fail due to conflict, or
     *         {@code null} if there is no such active {@code Transaction}
     */
    Transaction getConflictingTransaction(Transaction txn);

}
