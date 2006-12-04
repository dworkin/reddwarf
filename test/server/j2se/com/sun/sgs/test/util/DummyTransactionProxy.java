package com.sun.sgs.test.util;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

/** Provides a simple implementation of TransactionProxy, for testing. */
public class DummyTransactionProxy implements TransactionProxy {

    /** Stores information about the transaction for the current thread. */
    private final ThreadLocal<DummyTransaction> threadTxn =
	new ThreadLocal<DummyTransaction>();

    /** The task owner. */
    private final TaskOwner taskOwner = new DummyTaskOwner();

    /** Creates an instance of this class. */
    public DummyTransactionProxy() { }

    /* -- Implement TransactionProxy -- */

    public Transaction getCurrentTransaction() {
	Transaction txn = threadTxn.get();
	if (txn != null) {
	    return txn;
	} else {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
    }

    public TaskOwner getCurrentOwner() {
	return taskOwner;
    }

    /* -- Other public methods -- */

    /**
     * Specifies the transaction object that will be returned for the current
     * transaction, or null to specify that no transaction should be
     * associated.  Also stores itself in the transaction instance, so that the
     * transaction can clear the current transaction on prepare, commit, or
     * abort.
     */
    public void setCurrentTransaction(DummyTransaction txn) {
	threadTxn.set(txn);
	if (txn != null) {
	    txn.proxy = this;
	}
    }

    public <T extends Service> T getService(Class<T> type) {
	// TODO Auto-generated method stub
	return null;
    }
}
