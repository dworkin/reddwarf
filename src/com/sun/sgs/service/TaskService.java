
/*
 * TaskService.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Jul 13, 2006	 7:42:46 PM
 * Desc: 
 *
 */

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
