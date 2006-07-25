
package com.sun.sgs.service.impl;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.ManagedRunnable;

import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.kernel.ReferenceRunnable;
import com.sun.sgs.kernel.Task;
import com.sun.sgs.kernel.TransactionProxy;
import com.sun.sgs.kernel.TransactionRunnable;

import com.sun.sgs.service.NotPreparedException;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;

import java.util.HashSet;

import java.util.concurrent.ConcurrentHashMap;


/**
 * This is a simple implementation of <code>TaskService</code> that handles
 * any number of pending tasks, but makes no attempt to order or otherwise
 * prioritize the tasks.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleTaskService implements TaskService
{

    /**
     * The identifier for this <code>Service</code>.
     */
    public static final String IDENTIFIER =
        SimpleTaskService.class.getName();

    // the proxy used to access transaction state
    private TransactionProxy transactionProxy = null;

    // the state map for each active transaction
    private ConcurrentHashMap<Long,TxnState> txnMap;

    // the task queue used to actually queue up tasks
    private TaskQueue taskQueue = null;

    /**
     * Creates an instance of <code>SimpleTaskService</code>.
     */
    public SimpleTaskService() {
        txnMap = new ConcurrentHashMap<Long,TxnState>();
    }

    /**
     * Tells this service about the <code>TaskQueue</code> where future
     * tasks get queued.
     *
     * @param taskQueue the system's task queue
     */
    public void setTaskQueue(TaskQueue taskQueue) {
        this.taskQueue = taskQueue;
    }

    /**
     * Returns the identifier used to identify this service.
     *
     * @return the service's identifier
     */
    public String getIdentifier() {
        return IDENTIFIER;
    }

    /**
     * Provides this <code>Service</code> access to the current transaction
     * state.
     *
     * @param transactionProxy a non-null proxy that provides access to the
     *                         current <code>Transaction</code>
     */
    public void setTransactionProxy(TransactionProxy transactionProxy) {
        this.transactionProxy = transactionProxy;
    }

    /**
     * Tells the <code>Service</code> to prepare for commiting its
     * state assciated with the given transaction.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void prepare(Transaction txn) throws Exception {
        // get the transaction state & set it as prepared
        TxnState txnState = txnMap.get(txn.getId());
        txnState.prepared = true;

        // This is a simple enough service implementation that it actually
        // doesn't need to do any preparation, since (in the current
        // version of the system) you can't fail to add a task
        // NOTE: If the task queue starts to become size-limited,
        // then this prepare step will need to reserve space first
    }

    /**
     * Tells the <code>Service</code> to commit its state associated
     * with the previously prepared transaction.
     *
     * @param txn the <code>Transaction</code> state
     *
     * @throws NotPreparedException if prepare wasn't previously called
     *                              on this service for this transaction
     */
    public void commit(Transaction txn) throws NotPreparedException {
        // get the transaction state
        TxnState txnState = txnMap.get(txn.getId());

        // make sure we were prepared
        if (! txnState.prepared)
            throw new NotPreparedException("SimpleTaskService: Transaction " +
                                           txn.getId() + " not prepared");

        // now iterate through the tasks and put them in the queue
        for (ManagedReference<? extends ManagedRunnable> runnableRef :
                 txnState.tasks) {
            // create a task that handles the reference correctly
            Task task = new Task(new ReferenceRunnable(runnableRef), null);

            // now wrap the task in a transactional context and put this
            // into the task queue
            taskQueue.
                queueTask(new Task(new TransactionRunnable(task), null));
        }

        // finally, remove this transaction state from the map
        txnMap.remove(txn.getId());
    }

    /**
     * Tells the <code>Service</code> to both prepare and commit its
     * state associated with the given transaction. This is provided as
     * an optimization for cases where the sysem knows that a given
     * transaction cannot fail, or can be partially backed out.
     *
     * @param txn the <code>Transaction</code> state
     *
     * FIXME: what does this throw? (also, see comments in prepare)
     */
    public void prepareAndCommit(Transaction txn) throws Exception {
        prepare(txn);
        commit(txn);
    }

    /**
     * Tells the <code>Service</code> to abort its involvement with
     * the given transaction.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void abort(Transaction txn) {
        // remove the state so it can't be used any more
        txnMap.remove(txn.getId());
    }

    /**
     * Private helper that gets the transaction state, or creates it (and
     * joins to the transaction) if the state doesn't exist. This is only
     * used by the methods that follow in this class (ie, not prepare and
     * commit).
     */
    private TxnState getTxnState(Transaction txn) {
        // try to get the current state
        TxnState txnState = txnMap.get(txn.getId());

        // if it didn't exist yet then create it and joing the transaction
        if (txnState == null) {
            txnState = new TxnState();
            txnMap.put(txn.getId(), txnState);
            txn.join(this);
        } else {
            // if it's already been prepared then we shouldn't be using
            // it...note that this shouldn't be a problem, since the system
            // shouldn't let this case get tripped, so this is just defensive
            // FIXME: what exception should actually get thrown?
            if (txnState.prepared)
                throw new RuntimeException("Trying to access prepared txn");
        }

        return txnState;
    }

    /**
     * Queues a task to run.
     *
     * @param txn the transaction state
     * @param task the task to run
     */
    public void queueTask(Transaction txn,
                          ManagedReference<? extends ManagedRunnable> task) {
        // get the transaction state
        TxnState txnState = getTxnState(txn);

        // add the task to the collection
        txnState.tasks.add(task);
    }

    /**
     *
     */
    private class TxnState {
        // true if this state has been prepared, false otherwise
        public boolean prepared = false;

        // the set of delayed tasks
        public HashSet<ManagedReference<? extends ManagedRunnable>> tasks =
                new HashSet<ManagedReference<? extends ManagedRunnable>>();
    }

}
