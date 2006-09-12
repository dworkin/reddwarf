
package com.sun.sgs.service;

import com.sun.sgs.impl.kernel.Task;
import com.sun.sgs.impl.kernel.TransactionalTaskThread;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionCoordinator;


/**
 * This is the basic <code>Runnable</code> that is used to run any
 * task in a transactional context. This class handles creating the
 * transaction state, calling the task, and committing the task
 * once the task has finished.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class TransactionRunnable implements Runnable
{

    // the coordinator
    private static TransactionCoordinator transactionCoordinator = null;

    // the task to run
    private Task task;

    /**
     * Creates an instance of <code>TransactionRunnable</code>.
     *
     * @param transactionalTask the task to run in a transactional context
     */
    public TransactionRunnable(Task transactionalTask) {
        this.task = transactionalTask;
    }

    /**
     * One-time assignment for the <code>TransactionCoordinator</code>
     * <p>
     * FIXME: yes, this is a little ugly, but the important thing is to look
     * at the overall design...once that's a little more solid we'll re-visit
     * this method.
     */
    public static void setTransactionCoordinator(TransactionCoordinator
                                                 transactionCoordinator) {
        if (TransactionRunnable.transactionCoordinator == null)
            TransactionRunnable.transactionCoordinator =
                transactionCoordinator;
    }

    /**
     * Runs the task in a transactional state.
     */
    public void run() {
        // create the transaction and install it in the thread
        Transaction txn =
            transactionCoordinator.getTransaction(task.getQuality());
        TransactionalTaskThread thread =
            (TransactionalTaskThread)(Thread.currentThread());
        thread.setTransaction(txn);

        // run the task
        // FIXME: do we need to look for specific exceptions or other
        // error cases here? I think the answer is yes, but I'm not
        // sure we know how these are defined yet...
        task.run();

        // now try to commit the exception
        // FIXME: what do we actually do with this exception? Does this
        // get folded back into the re-try mechanism?
        try {
            txn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // finally, clear the transaction state from the thread
        thread.clearTransaction(txn);
    }

}
