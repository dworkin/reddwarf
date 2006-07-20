
package com.sun.sgs.service;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.ManagedRunnable;


/**
 * This type of <code>Service</code> handles queuing tasks.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface TaskService extends Service
{

    /**
     * Queues a task to run.
     *
     * @param txn the <code>Transaction</code> state
     * @param task the task to run
     */
    public void queueTask(Transaction txn,
			  ManagedReference<? extends ManagedRunnable> task);

}
