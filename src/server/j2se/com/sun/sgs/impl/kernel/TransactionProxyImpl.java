/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.TaskOwner;

import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;


/**
 * This is a proxy that provides access to the currently active
 * <code>Transaction</code> and its owner. Because <code>Service</code>s are
 * the only components outside of the kernel that should have visibility into
 * this state, they are the only components in the system that are provided
 * a reference to the usable proxy.
 *
 * @since 1.0
 * @author Seth Proctor
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
        return ((TransactionalTaskThread)(Thread.currentThread())).
            getCurrentTransaction();
    }

    /**
     * {@inheritDoc}
     */
    public TaskOwner getCurrentOwner() {
        return ((TaskThread)(Thread.currentThread())).getCurrentOwner();
    }

    /**
     * {@inheritDoc}
     */
    public <T extends Service> T getService(Class<T> type) {
        return ((AbstractKernelAppContext)(getCurrentOwner().getContext())).
            getService(type);
    }


}
