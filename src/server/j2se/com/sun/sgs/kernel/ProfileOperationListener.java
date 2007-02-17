
package com.sun.sgs.kernel;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;

import java.util.List;


/**
 * This interface is used to listen for profiling data as reported by the
 * system. Unlike the individual operations provided to
 * <code>ProfilingConsumer</code>, the data provided here is aggregated
 * data representing events in the scheduler or collected data about a
 * complete task run through the scheduler.
 * <p>
 * In order to create listeners with all of the facilities that they need,
 * all implementations of <code>ProfileOperationListener</code> must
 * implement a constructor of the form (<code>java.util.Properties</code>,
 * <code>com.sun.sgs.kernel.TaskOwner</code>,
 * <code>com.sun.sgs.kernel.TaskScheduler</code>,
 * <code>com.sun.sgs.kernel.ResourceCoordinator</code>).
 * <p>
 * Note that this interface is not complete. It is provided as an initial
 * attempt to capture basic aspects of operation. As more profiling and
 * investigation is done on the system, expect to see the information
 * provided here evolve.
 */
public interface ProfileOperationListener {

    /**
     * Notifies this listener of a registered operation that may be reported
     * as part of the data provided to <code>report</code>. If a listener
     * is created after an operation has already been registered, then the
     * listener will be notified of that operation before any tasks are
     * reported.
     *
     * @param op a registered <code>ProfiledOperation</code>
     */
    public void notifyNewOp(ProfiledOperation op);

    /**
     * Notifies the listener of the number of threads being used by the
     * scheduler to run tasks. This is typically called when the count
     * changes, or when a listener is first created.
     *
     * @param schedulerThreadCount the number of consumer threads being
     *                             used by the scheduler
     */
    public void notifyThreadCount(int schedulerThreadCount);

    /**
     * Reports a completed task that has been run through the scheduler. The
     * task may have completed successfully or may have failed. If a
     * task is re-tried, then this method will be called multiple times for
     * each re-try of the same task. Note that in this case the
     * <code>scheduledStartTime</code> will remain constant but the
     * <code>actualStartTime</code> will change for each re-try of the
     * same task.
     *
     * @param task the <code>KernelRunnable</code> that was run
     * @param transactional whether any part of the task ran transactionally
     * @param owne the <code>TaskOwner</code> for the task
     * @param scheduledStartTime the requested starting time for the task in
     *                           milliseconds since January 1, 1970
     * @param actualStartTime the actual starting time for the task in
     *                        milliseconds since January 1, 1970
     * @param runningTime the length in milliseconds to execute the task
     * @param ops a <code>List</code> of <code>ProfiledOperation</code>
     *            representing the ordered set of reported operations
     * @param retryCount the number of times this task has bee tried
     * @param succeeded <code>true</code> if this task completed successfully,
     *                  <code>false</code> if it failed
     */
    public void report(KernelRunnable task, boolean transactional,
                       TaskOwner owner, long scheduledStartTime,
                       long actualStartTime, long runningTime,
                       List<ProfiledOperation> ops, int retryCount,
                       boolean succeeded);

    /**
     * Tells this listener that the system is shutting down.
     */
    public void shutdown();

}
