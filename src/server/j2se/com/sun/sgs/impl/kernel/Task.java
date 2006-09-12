
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedRunnable;
import com.sun.sgs.app.Quality;

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
 * node identifier, etc.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class Task implements ManagedRunnable
{

    // the task to run
    private Runnable runnable;

    // the application context
    private AppContext appContext;

    // the quality of service paramaters
    private Quality quality;

    /**
     * Creates an instance of <code>Task</code>
     * <p>
     * FIXME: this may still want more meta-data, like user
     *
     * @param runnable the actual task to run
     * @param appContext the context of the runing application
     * @param quality the requested quality of service paramaters
     */
    public Task(Runnable runnable, AppContext appContext, Quality quality) {
        this.runnable = runnable;
        this.appContext = appContext;
        this.quality = quality;
    }

    /**
     * Returns the context for the application running this task.
     *
     * @return the task's <code>AppContext</code>
     */
    public AppContext getAppContext() {
        return appContext;
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
