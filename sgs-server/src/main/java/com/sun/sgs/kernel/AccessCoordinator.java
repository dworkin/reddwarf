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
 * A central point for tracking all shared objects accessed in the context
 * of a transaction, and managing any conflict these accesses may cause.
 * In this case, "shared object" means any object that is accessible within
 * any transaction on any node. An example would be a {@code ManagedObject}
 * as provided by the {@code DataService}.
 * <p>
 * Implementations should require that each given {@code AccessReporter} will
 * report on a unique set of objects. That is, even if two different
 * {@code AccessReporter} instances report access to an object with the
 * same identifier, it should be assumed that these are different objects.
 * Think of each {@code AccessReporter} as reporting on objects within a
 * unique namespace.
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

    // TEST
    void validate(Transaction txn);

}
