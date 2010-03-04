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

import com.sun.sgs.auth.Identity;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;


/**
 * This is a proxy that provides access to the currently active
 * <code>Transaction</code> and its owner. Because <code>Service</code>s are
 * the only components outside of the kernel that should have visibility into
 * this state, they are the only components in the system that are provided
 * a reference to the usable proxy.
 */
final class TransactionProxyImpl implements TransactionProxy {

    /**
     * This package-private constructor creates a new instance of
     * <code>TransactionProxyImpl</code>. It is package-private so that
     * only the kernel components can create instances for access to the
     * current <code>Transaction</code>.
     */
    TransactionProxyImpl() {
        
    }

    /**
     * {@inheritDoc}
     */
    public Transaction getCurrentTransaction() {
        return ContextResolver.getCurrentTransaction();
    }

    /**
     * {@inheritDoc}
     */
    public boolean inTransaction() {
        return ContextResolver.inTransaction();
    }
    
    /**
     * {@inheritDoc}
     */
    public Identity getCurrentOwner() {
        return ContextResolver.getCurrentOwner();
    }

    /**
     * {@inheritDoc}
     */
    public <T extends Service> T getService(Class<T> type) {
        return ContextResolver.getContext().getService(type);
    }


}
