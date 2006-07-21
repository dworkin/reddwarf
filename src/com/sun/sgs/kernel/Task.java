
package com.sun.sgs.kernel;

import com.sun.sgs.ManagedRunnable;
import com.sun.sgs.Quality;

import com.sun.sgs.service.Transaction;


/**
 * This is a package-private interface used to define a single task to
 * execute. It handles the target to run, the transaction state, and
 * any associated meta-data or quality of service parameters. This is
 * typically stored in the state of a <code>TaskThread</code> and then
 * consumed from the task queue.
 * <p>
 * FIXME: This still needs to provide accessor methods to meta-data like
 * app identifier, node identifier, etc.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
interface Task extends ManagedRunnable
{

    /**
     * Returns the transaction state associated with this task.
     *
     * @return the task's <code>Transaction</code>
     */
    public Transaction getTransaction();

    /**
     * Returns the quality of service parameters associated with this task.
     *
     * @return the task's <code>Quality</code>
     */
    public Quality getQuality();

    /**
     * Runs the task.
     */
    public void run();

}
