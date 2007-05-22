/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * Package-private utility class that runs a task in a transactional
 * context with unbounded timeout.
 */
class UnboundedTransactionRunner implements KernelRunnable {

    // the kernel runnable
    private final KernelRunnable transactionalTask;

    /**
     * Creates an instance of <code>UnboundedTransactionRunner</code> that
     * accepts a <code>KernelRunnable</code> to run in a transactional context
     * with unbounded timeout.
     *
     * @param transactionalTask the <code>KernelRunnable</code> to run
     */
    UnboundedTransactionRunner(KernelRunnable transactionalTask) {
	if (transactionalTask == null)
            throw new NullPointerException("null task not allowed");
        this.transactionalTask = transactionalTask;
    }

    /**
     * Returns the base type of the task that is run transactionally by
     * this <code>TransactionRunner</code>.
     *
     * @return the base type of the wrapped task
     */
    public String getBaseTaskType() {
        return transactionalTask.getBaseTaskType();
    }

    /**
     * Runs this <code>TransactionRunner</code>, which in turn runs its
     * <code>KernelRunnable</code> in a transactional context with unbounded
     * timeout.
     *
     * @throws Exception if an error occurs with the
     *                   <code>KernelRunnable</code> or with the transaction
     */
    public void run() throws Exception {
        TaskHandler.runTransactionalTask(transactionalTask, true);
    }

}
