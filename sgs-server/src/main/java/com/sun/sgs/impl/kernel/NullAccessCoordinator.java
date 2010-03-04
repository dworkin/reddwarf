/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
