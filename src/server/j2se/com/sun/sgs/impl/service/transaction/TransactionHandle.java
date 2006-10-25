package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.service.Transaction;

/** Defines an interface for managing a transaction. */
public interface TransactionHandle {

    /**
     * Returns the active transaction associated with this handle.
     *
     * @return	the transaction
     * @throws	TransactionNotActive if the transaction associated with this
     *		handle is not active
     */
    Transaction getTransaction();

    /**
     * Prepares and commits the transaction associated with this handle.
     *
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	TransactionAbortedException if the transaction was aborted
     *		during preparation without an exception being thrown
     * @throws	Exception if any participant throws an exception while
     *		preparing the transaction
     */
    void commit() throws Exception;
}
