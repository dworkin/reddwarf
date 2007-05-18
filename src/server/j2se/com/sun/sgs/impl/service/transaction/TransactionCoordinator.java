/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.transaction;

/** Defines an interface for managing transactions. */
public interface TransactionCoordinator {

    /** The property used to specify the timeout value for transactions. */
    public static final String TXN_TIMEOUT_PROPERTY =
	"com.sun.sgs.txn.timeout";

    /** The property used to specify the value for unbounded timeout. */
    public static final String TXN_UNBOUNDED_TIMEOUT_PROPERTY =
	"com.sun.sgs.txn.timeout.unbounded";

    /**
     * Creates a handle to a transaction with either a bounded or unbounded
     * timeout value.
     * 
     * @param unbounded <code>true</code> if this transaction's timeout is
     *                  unbounded, <code>false</code> otherwise
     *
     * @return	a handle for managing the newly created transaction.
     */
    TransactionHandle createTransaction(boolean unbounded);
}
