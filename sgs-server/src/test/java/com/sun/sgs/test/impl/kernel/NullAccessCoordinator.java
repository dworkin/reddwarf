/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.impl.kernel.AbstractAccessCoordinator;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;

/**
 * Define an {@link AccessCoordinatorHandle} that does nothing, for performance
 * testing.
 */
public class NullAccessCoordinator extends AbstractAccessCoordinator {

    /**
     * Creates an instance of this class.
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
	super(txnProxy, profileCollectorHandle);
    }

    /* -- Implement AccessCoordinator -- */

    /** {@inheritDoc} */
    public <T> AccessReporter<T> registerAccessSource(
	String sourceName, Class<T> objectIdType)
    {
	checkNull("objectIdType", objectIdType);
	return new AccessReporterImpl<T>(sourceName);
    }

    /** {@inheritDoc} */
    public Transaction getConflictingTransaction(Transaction txn) {
	checkNull("txn", txn);
	return null;
    }

    /* -- Implement AccessCoordinatorHandle -- */

    /** {@inheritDoc} */
    public void notifyNewTransaction(
	Transaction txn, long requestedStartTime, int tryCount)
    {
	checkNull("txn", txn);
	if (requestedStartTime < 0) {
	    throw new IllegalArgumentException(
		"The requestedStartTime must not be less than 0");
	} else if (tryCount < 1) {
	    throw new IllegalArgumentException(
		"The tryCount must not be less than 1");
	}
    }

    /* -- Other classes -- */

    /** Implement {@link AccessReporter}. */
    private class AccessReporterImpl<T> extends AbstractAccessReporter<T> {

	/**
	 * Creates an instance of this class.
	 *
	 * @param	source the source of the objects managed by this
	 *		reporter
	 */
	AccessReporterImpl(String source) {
	    super(source);
	}

	/* -- Implement AccessReporter -- */

	/** {@inheritDoc} */
	public void reportObjectAccess(
	    Transaction txn, T objectId, AccessType type, Object description)
	{
	    checkNull("txn", txn);
	    checkNull("objectId", objectId);
	    checkNull("type", type);
	}

	/** {@inheritDoc} */
	public void setObjectDescription(
	    Transaction txn, T objectId, Object description)
	{
	    checkNull("txn", txn);
	    checkNull("objectId", objectId);
	}
    }
}
