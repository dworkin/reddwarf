
package com.sun.sgs.kernel;

import com.sun.sgs.app.TaskRejectedException;


/**
 * This interface is used to start long-running tasks (for example, a consumer
 * thread or a select loop) that need their own thread of control. Unlike
 * tasks submitted to <code>TaskScheduler</code>, no attempt is made to
 * re-try long-running tasks.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface ResourceCoordinator
{

    /**
     * Requests that the given task run in its own thread of control. This
     * should only be done for long-running tasks.
     *
     * @param task the <code>Runnable</code> to start in its own thread
     * @param component the component that manages the task
     *
     * @throws TaskRejectedException if the resources cannot be allocated
     *                               for the component's task
     */
    public void startTask(Runnable task, Manageable component);

}
