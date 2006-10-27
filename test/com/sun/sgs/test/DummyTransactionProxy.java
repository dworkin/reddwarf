package com.sun.sgs.test;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

public class DummyTransactionProxy implements TransactionProxy {
    private ThreadLocal<DummyTransaction> threadTxn =
	new ThreadLocal<DummyTransaction>();
    private final TaskOwner taskOwner = new DummyTaskOwner();
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
    public void setCurrentTransaction(DummyTransaction txn) {
	threadTxn.set(txn);
	if (txn != null) {
	    txn.proxy = this;
	}
    }
}
