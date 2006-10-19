package com.sun.sgs.test;

import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

public class DummyTransactionProxy implements TransactionProxy {
    private Transaction txn;
    public Transaction getCurrentTransaction() {
	return txn;
    }
    public void setCurrentTransaction(Transaction txn) {
	this.txn = txn;
    }
}
