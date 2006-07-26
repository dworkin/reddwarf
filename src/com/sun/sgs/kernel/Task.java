
package com.sun.sgs.kernel;

import com.sun.sgs.ManagedRunnable;
import com.sun.sgs.Quality;

import com.sun.sgs.service.Transaction;


/**
 * This class is used to define a single task to execute. It handles the
 * target to run and any associated meta-data or quality of service
 * parameters. This is typically stored in the state of a
 * <code>TaskThread</code> and then consumed from the task queue. Note
 * that <code>Task</code> is nothing more than a light-weight container
 * used to pair actions with meta-data. As such, it's not uncommon to
 * have tasks contain actions that in turn contains tasks.
 * <p>
 * FIXME: This still needs to provide accessor methods to meta-data like
 * app identifier, node identifier, etc.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class Task implements ManagedRunnable
{

    // the task to run
    private Runnable runnable;

    // the quality of service paramaters
    private Quality quality;

    /**
     * Creates an instance of <code>Task</code>
     * <p>
     * FIXME: this also needs to take meta-data parameters
     *
     * @param runnable the actual task to run
     * @param quality the requested quality of service paramaters
     */
    public Task(Runnable runnable, Quality quality) {
        this.runnable = runnable;
        this.quality = quality;
    }

    /**
     * Returns the quality of service parameters associated with this task.
     *
     * @return the task's <code>Quality</code>
     */
    public Quality getQuality() {
        return quality;
    }

    /**
     * Runs the task.
     */
    public void run() {
        runnable.run();
    }

}
