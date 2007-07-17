/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.TransactionTimeoutException;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * Runnable that waits for some time and then tries to access the current
 * transaction state. Used to test transaction timeout.
 */
public class TxnTimeoutTestRunnable implements Runnable {

    private final long delay;
    private final boolean unbounded;
    public boolean timedOut = false;

    /**
     * Creates an instance that waits the specified time during a bounded
     * or unbounded transaction.
     */
    public TxnTimeoutTestRunnable(long delay, boolean unbounded) {
	this.delay = delay;
	this.unbounded = unbounded;
    }

    public void run() {
	try {
	    TaskHandler.runTransactionalTask(new TxnTask(), unbounded);
	} catch (Exception e) {}
    }

    private class TxnTask implements KernelRunnable {
	public void run() throws Exception {
	    Thread.sleep(delay);

	    try {
	        ThreadState.getCurrentTransaction();
	    } catch (TransactionTimeoutException tte) {
		timedOut = true;
	    }
	}
	public String getBaseTaskType() {
	    return TxnTimeoutTestRunnable.class.getName();
	}
    }

}
