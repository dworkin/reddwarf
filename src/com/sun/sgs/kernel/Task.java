
package com.sun.sgs.kernel;

import com.sun.sgs.ManagedRunnable;
import com.sun.sgs.Quality;

import com.sun.sgs.service.Transaction;


/**
 * A <code>Task</code> is the basic unit of work in the system.
 * It is simply a Runnable with some associated meta-data (such
 * as the task's owner and quality of service properties).
 * It's not uncommon to have tasks contain actions that in turn
 * contains tasks.
 * <p>
 * FIXME: This still needs to provide accessor methods to meta-data like
 * app identifier, node identifier, etc.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class Task implements Runnable
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
