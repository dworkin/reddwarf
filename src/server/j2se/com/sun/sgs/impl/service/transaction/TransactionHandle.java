package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.service.Transaction;

/** Defines an interface for managing a transaction. */
public interface TransactionHandle {

    /**
     * Returns the transaction associated with this handle.
     *
     * @return	the transaction
     */
    Transaction getTransaction();

    /**
     * Prepares and commits the transaction associated with this handle.
     *
     * @throws	TransactionNotActiveException if the transaction is not active
     * @throws	TransactionAbortedException if the transaction was aborted
     * @throws	Exception if any participant throws an exception while
     *		preparing the transaction
     */
    void commit() throws Exception;

    /**
     * Returns whether the transaction is currently active.  An active
     * transaction is one that has not been prepared, committed, or aborted.
     *
     * @return	<code>true</code> if the transaction is active, otherwise
     *		<code>false</code>
     */
    boolean isActive();
}
