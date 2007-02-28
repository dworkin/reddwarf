/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.transaction;

/** Defines an interface for managing transactions. */
public interface TransactionCoordinator {

    /**
     * Creates a transaction.
     *
     * @return	a handle for managing the newly created transaction.
     */
    TransactionHandle createTransaction();
}
