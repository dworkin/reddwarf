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
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

/** Provides a skeletal implementation of {@code AccessCoordinatorHandle}. */
public abstract class AbstractAccessCoordinator
    implements AccessCoordinatorHandle
{
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
	checkNull("txnProxy", txnProxy);
	checkNull("profileCollectorHandle", profileCollectorHandle);
	this.txnProxy = txnProxy;
	this.profileCollectorHandle = profileCollectorHandle;
    }

    /**
     * Provides a skeletal implementation of {@code AccessReporter}, supplying
     * the overloadings of {@code reportObjectAccess} and {@code
     * setObjectDescription} that supply default arguments.  Subclasses should
     * at least provide implementations of the four argument overloading of
     * {@code reportObjectAccess} and the three argument overloading of {@code
     * setObjectDescription}.
     */
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
	    checkNull("source", source);
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
