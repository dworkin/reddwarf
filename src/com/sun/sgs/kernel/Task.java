package com.sun.sgs.kernel;

import com.sun.sgs.ManagedRunnable;
import com.sun.sgs.Quality;
import com.sun.sgs.User;
import com.sun.sgs.kernel.scheduling.Priority;
import com.sun.sgs.service.Transaction;


/**
 * This class is used to define a single task to execute. It handles
 * the target to run and any associated meta-data or quality of
 * service parameters. This is typically stored in the state of a
 * {@link TaskThread} and then consumed by the {@link
 * com.sun.sgs.kernel.scheduling.TaskScheduler}. 
 * 
 * <p>
 *
 * Note that <code>Task</code> is nothing more than a light-weight
 * container used to pair actions with meta-data. As such, it's not
 * uncommon to have tasks contain actions that in turn contains tasks.
 *
 * <p> 
 *
 * FIXME: This still needs to provide accessor methods to
 * meta-data like node identifier, etc.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 * @author David Jurgens
 */
public interface Task extends ManagedRunnable {

    /**
     * Returns the context for the application running this task.
     *
     * @return the task's <code>AppContext</code>
     */
    public AppContext getAppContext();

    /**
     * Returns the quality of service parameters associated with this task.
     *
     * @return the task's <code>Quality</code>
     */
    public Quality getQuality();

    /**
     * Returns the priority at which this task should be run.
     * Priorities are relative only to the <code>AppContext</code> and
     * <code>User</code>.
     *
     * @return the suggested priority for this task.
     */
    public Priority getPriority();

    /**
     * Returns the <code>User</code> on whose behalf this task is run.
     *
     * @return the associated <code>User</code>
     */
    public User getUser();

    /**
     * Runs the task.
     */
    public void run();

}
