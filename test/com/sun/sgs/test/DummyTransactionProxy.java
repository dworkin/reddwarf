package com.sun.sgs.test;

import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

public class DummyTransactionProxy implements TransactionProxy {
    private Transaction txn;
    private final TaskOwner taskOwner = new DummyTaskOwner();
    public Transaction getCurrentTransaction() {
	return txn;
    }
    public TaskOwner getCurrentOwner() {
	return taskOwner;
    }
    public void setCurrentTransaction(Transaction txn) {
	this.txn = txn;
    }
}
