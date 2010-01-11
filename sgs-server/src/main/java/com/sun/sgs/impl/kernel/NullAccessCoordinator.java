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
 * --
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;

/**
 * Define an {@link AccessCoordinatorHandle} that does not detect conflicts,
 * report accesses to the profiling system, or perform any error checking on
 * its arguments.
 */
public class NullAccessCoordinator
    implements AccessCoordinatorHandle, AccessReporter<Object>
{
    /** Creates an instance of this class. */
    public NullAccessCoordinator() { }

    /**
     * Creates an instance of this class, accepting the standard arguments for
     * access coordinators, which are ignored.
     *
     * @param	properties the configuration properties
     * @param	txnProxy the transaction proxy
     * @param	profileCollectorHandle the profile collector handle
     */
    public NullAccessCoordinator(
	Properties properties,
	TransactionProxy txnProxy,
	ProfileCollectorHandle profileCollectorHandle)
    {
    }

    /* -- Implement AccessCoordinatorHandle -- */

    /** {@inheritDoc} */
    public <T> AccessReporter<T> registerAccessSource(
	String sourceName, Class<T> objectIdType)
    {
	return (AccessReporter<T>) this;
    }

    /** {@inheritDoc} */
    public Transaction getConflictingTransaction(Transaction txn) {
	return null;
    }

    /** {@inheritDoc} */
    public void notifyNewTransaction(
	Transaction txn, long requestedStartTime, int tryCount) {
    }

    /* -- Implement AccessReporter -- */

    /** {@inheritDoc} */
    public void reportObjectAccess(Object objectId, AccessType type) { }

    /** {@inheritDoc} */
    public void reportObjectAccess(
	Transaction txn, Object objectId, AccessType type)
    {
    }

    /** {@inheritDoc} */
    public void reportObjectAccess(
	Object objectId, AccessType type, Object description)
    {
    }

    /** {@inheritDoc} */
    public void reportObjectAccess(
	Transaction txn, Object objectId, AccessType type, Object description)
    {
    }

    /** {@inheritDoc} */
    public void setObjectDescription(Object objectId, Object description) { }

    /** {@inheritDoc} */
    public void setObjectDescription(
	Transaction txn, Object objectId, Object description)
    {
    }
}
