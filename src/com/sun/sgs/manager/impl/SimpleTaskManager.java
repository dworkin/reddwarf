
package com.sun.sgs.manager.impl;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.ManagedRunnable;

import com.sun.sgs.kernel.TransactionProxy;

import com.sun.sgs.manager.TaskManager;

import com.sun.sgs.service.Transaction;


/**
 * This is a simple implementation of <code>TaskManager</code> that is the
 * default used.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleTaskManager extends TaskManager
{

    // the proxy used to access transaction state
    private TransactionProxy transactionProxy;

    /**
     * Creates an instance of <code>SimpleTaskManager</code>.
     *
     * @param transactionProxy the proxy used to access transaction state
     */
    public SimpleTaskManager(TransactionProxy transactionProxy) {
        super();

        this.transactionProxy = transactionProxy;
    }

    /**
     * Queues a task to run.
     *
     * @param taskReference the task to run
     */
    public void queueTask(ManagedReference<? extends ManagedRunnable>
            taskReference) {
        Transaction txn = transactionProxy.getCurrentTransaction();
        txn.getTaskService().queueTask(txn, taskReference);
    }

}
