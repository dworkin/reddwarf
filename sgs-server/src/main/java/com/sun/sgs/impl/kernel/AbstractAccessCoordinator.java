/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

/** Provides a skeletal implementation of {@code AccessCoordinator}. */
public abstract class AbstractAccessCoordinator implements AccessCoordinator {

    /** The transaction proxy. */
    protected final TransactionProxy txnProxy;

    /** The profile collector handle. */
    protected final ProfileCollectorHandle profileCollectorHandle;
    
    /**
     * Creates an instance of this class.
     *
     * @param	txnProxy the transaction proxy
     * @param	profileCollectorHandle the profile collector handle
     */
    protected AbstractAccessCoordinator(
	TransactionProxy txnProxy,
	ProfileCollectorHandle profileCollectorHandle)
    {
	checkNonNull(txnProxy, "txnProxy");
	checkNonNull(profileCollectorHandle, "profileCollectorHandle");
	this.txnProxy = txnProxy;
	this.profileCollectorHandle = profileCollectorHandle;
    }

    /**
     * Checks that the argument is non-null.
     *
     * @param	arg the argument
     * @param	parameterName the parameter name for the argument
     * @throws	NullPointerException if {@code arg} is null
     */
    protected static void checkNonNull(Object arg, String parameterName) {
	if (arg == null) {
	    throw new NullPointerException(
		"The " + parameterName + " argument must not be null");
	}
    }

    /** Provide a skeletal implementation of {@code AccessReporter}. */
    public abstract class AbstractAccessReporter<T>
	implements AccessReporter<T>
    {
	/** The source for objects managed by this reporter. */
	protected final String source;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	source the source for objects managed by this reporter
	 */
        protected AbstractAccessReporter(String source) {
	    checkNonNull(source, "source");
            this.source = source;
        }

	/* -- Implement AccessReporter -- */

        /** {@inheritDoc} */
        public void reportObjectAccess(T objId, AccessType type) {
	    reportObjectAccess(
		txnProxy.getCurrentTransaction(), objId, type, null);
        }

        /** {@inheritDoc} */
        public void reportObjectAccess(
	    Transaction txn, T objId, AccessType type)
	{
            reportObjectAccess(txn, objId, type, null);
        }

        /** {@inheritDoc} */
	public void reportObjectAccess(
	    T objId, AccessType type, Object description)
	{
	    reportObjectAccess(
		txnProxy.getCurrentTransaction(), objId, type, description);
	}

        /** {@inheritDoc} */
	public void setObjectDescription(T objId, Object description) {
	    setObjectDescription(
		txnProxy.getCurrentTransaction(), objId, description);
        }
    }
}
