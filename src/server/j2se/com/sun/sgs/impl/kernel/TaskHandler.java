
package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionHandle;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;

import com.sun.sgs.service.Transaction;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This utility class provided by the kernel is used to run tasks. Unlike the
 * scheduler, which accepts tasks to run at some point in the future,
 * <code>TaskHandler</code> is the facility that actually invokes tasks.
 * It is needed to set the owner of tasks and create transactional context
 * for transactional tasks.
 * <p>
 * Note that this class enforces a singleton pattern. While anyone may
 * use this class to create transactional context, only with a reference
 * to the single instance may the owner of a task be changed, and only
 * trusted components are provided with this reference. Most components
 * outside the kernel will use the <code>TaskScheduler</code> or the
 * <code>TaskService</code> to run tasks.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public final class TaskHandler {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(TaskHandler.class.getName()));

    // the single instance used for creating transactions
    private static TransactionCoordinator transactionCoordinator = null;

    /**
     * Package-private constructor used by the kernel to create the single
     * instance of <code>TaskHandler</code>.
     *
     * @param transactionCoordinator the <code>TransactionCoordinator</code>
     *                               used to create new transactions
     *
     * @throws IllegalStateException if there already exists an instance
     *                               of <code>TaskHandler</code>
     */
    TaskHandler(TransactionCoordinator transactionCoordinator) {
        if (TaskHandler.transactionCoordinator != null)
            throw new IllegalStateException("an instance already exists");
        if (transactionCoordinator == null)
            throw new NullPointerException("null coordinator not allowed");

        logger.log(Level.CONFIG, "Creating the Task Handler");

        TaskHandler.transactionCoordinator = transactionCoordinator;
    }

    /**
     * Changes context to that of the given <code>TaskOwner</code> and
     * runs the given <code>KernelRunnable</code>. This is a non-static
     * method, and so only trusted components that have a reference to the
     * valid <code>TaskHandler</code> may run tasks as different owners.
     *
     * @param task the <code>KernelRunnable</code> to run
     * @param owner the <code>TaskOwner</code> for the given task
     *
     * @throws Exception if there are any failures running the task
     */
    public void runTaskAsOwner(KernelRunnable task, TaskOwner owner)
        throws Exception
    {
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "running a task as {0}", owner);

        // get the thread and the calling owner
        TaskThread thread = (TaskThread)(Thread.currentThread());
        TaskOwner parent = thread.getCurrentOwner();

        // change to the context of the new owner and run the task
        thread.setCurrentOwner(owner);
        try {
            task.run();
        } finally {
            // always restore the previous owner
            thread.setCurrentOwner(parent);
        }
    }

    /**
     * Runs the given task in a transactional state, committing the
     * transaction on completion of the task.
     *
     * @param task the <code>KernelRunnable</code> to run transactionally
     *
     * @throws Exception if there is any failure in running the task or
     *                   committing the transaction
     */
    public static void runTransactionalTask(KernelRunnable task)
        throws Exception
    {
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "starting a new transactional context");

        // create a new transaction and set it as currently active
        TransactionHandle handle = transactionCoordinator.createTransaction();
        Transaction transaction = handle.getTransaction();
        TransactionalTaskThread thread =
            (TransactionalTaskThread)(Thread.currentThread());
        thread.setCurrentTransaction(transaction);

        // run the task, and clear the active state as soon as we're done
        // so no one else sees it as active
        try {
            task.run();
        } catch (Exception e) {
            logger.logThrow(Level.FINE, "exception in txn {0}, aborting:",
        	    e, transaction);
            try {
        	transaction.abort();
            } catch (IllegalStateException ise) { /* FIXME: ignore? */ }
            throw e;
        } finally {
            thread.clearCurrentTransaction(transaction);
        }

        // finally, actually commit the transaction, allowing any exceptions
        // to be thrown, since they will indicate whether this task is
        // re-tried
        handle.commit();
    }

}
