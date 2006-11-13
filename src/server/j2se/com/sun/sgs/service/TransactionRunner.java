
package com.sun.sgs.service;

import com.sun.sgs.impl.kernel.TaskHandler;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * This is the basic <code>KernelRunnable</code> that is used to run any
 * task in a transactional context. This class handles creating the
 * transaction state, running a task, and committing the transaction
 * once the task has finished.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class TransactionRunner implements KernelRunnable {

    // the kernel runnable
    private KernelRunnable transactionalTask;

    /**
     * Creates an instance of <code>TransactionRunner</code> that accepts
     * a <code>KernelRunnable</code> to run in a transactional context.
     *
     * @param transactionalTask the <code>KernelRunnable</code> to run
     */
    public TransactionRunner(KernelRunnable transactionalTask) {
        this.transactionalTask = transactionalTask;
    }

    /**
     * Runs this <code>TransactionRunner</code>, which in turn runs its
     * <code>KernelRunnable</code> in a transactional context.
     *
     * @throws Exception if an error occurs with the
     *                   <code>KernelRunnable</code> or with the transaction
     */
    public void run() throws Exception {
        TaskHandler.runTransactionalTask(transactionalTask);
    }

}
