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
 * of a transaction, and managing any contention that this causes.
 */
public interface AccessCoordinator {

    /**
     * Register as a source of possible contention. This should be used
     * by any component that is providing access to shared objects (e.g.,
     * the {@code DataService} which provides access to
     * {@code ManagedObject}s and name bindings). This ensures that
     * contention is managed for access to the reported objects, and
     * that a report of all accesses is available through profiling.
     *
     * @param sourceName the name of the source of objects
     *
     * @return an {@code AccessNotifier} used to notify the system
     *         of access to shared objects
     */
    public AccessNotifier registerContentionSource(String sourceName);

    /**
     * The idea here is that a resolver needs to know who else might be
     * interested in a given object...but is this the right approach? The
     * reasoning here is that the Coodrinator should implement basic
     * conflict and deadlock detection so that each resolver doesn't have
     * to, but deadlock may depend on how resolution is done. So, maybe
     * the resolver is responsible for this, in which case it needs to
     * know about each access, not just possible contention cases. Does
     * this suggest that the Coordinator just tracks access, reports
     * profilie, etc. but passes on all access requests to the resolver?
     * ...
     * Maybe all requests get pased on, but the Coordinator implements a
     * utility method to see if there is basic contention happening?
     */
    //public List<> getCurrentReaders(Object obj);
    //public List<> getCurrentWriters(Object obj);

    /**
     * Find out what transaction, if still active, caused the given
     * transaction to fail due to conflict.
     *
     * @param txn a {@code Transaction} that failed due to conflict
     *
     * @return the active {@code Transaction} that causes the provided
     *         {@code Transaction} to fail due to contention, or
     *         {@code null} if there is no such active {@code Transaction}
     */
    public Transaction getConflictingTransaction(Transaction txn);

}
