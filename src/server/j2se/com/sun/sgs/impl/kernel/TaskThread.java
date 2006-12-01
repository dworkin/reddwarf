
package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.TaskOwner;


/**
 * This abstract implementation of <code>Thread</code> represents all of the
 * worker threads in the system. It is used by the
 * <code>ResourceCoordinator</code> to manage the available threads for
 * running long-lived tasks. Note that these are the tasks run through this
 * interface, and not the short-lived tasks queued in the scheduler. The
 * <code>startTask</code> method on <code>ResourceCoordinator</code> should
 * be called infrequently, so <code>TaskThread</code>s will not be given
 * new tasks frequently.
 * <p>
 * This interface provides two major facilities. First, it allows assigning
 * new tasks (only if a task isn't currently running), or shutting down the
 * thread (once no task is running). These are used so that existing threads
 * can be re-cycled. Second, it keeps track of the current owner of this
 * thread, which starts as the owner of the initial task, but will be
 * re-set with each task run out of the scheduler. See
 * <code>TaskHandler</code> for details on this process.
 *
 * @since 1.0
 * @author Seth Proctor
 */
abstract class TaskThread extends Thread {

    // the resource coordinator that manages this thread
    private final ResourceCoordinatorImpl resourceCoordinator;

    // flag that tracks whether this thread is running, and whether it's
    // waiting for a task to execute
    private boolean running;
    private boolean waiting;

    // the current owner of this thread
    private TaskOwner currentOwner;

    // the task we're running next
    private Runnable nextTask;

    /**
     * Creates a new instance of <code>TaskThread</code>.
     *
     * @param resourceCoordinator the coordinator for this thread
     */
    protected TaskThread(ResourceCoordinatorImpl resourceCoordinator) {
        this.resourceCoordinator = resourceCoordinator;
        running = false;
        waiting = false;
        currentOwner = Kernel.TASK_OWNER;
        nextTask = null;
    }

    /**
     * Returns the current owner of the work being done by this thread.
     * Depending on what is currently running in this thread, the owner may
     * be the owner of the last <code>Runnable</code> provided to
     * <code>runTask</code>, the owner of a specific
     * <code>KernelRunnable</code> running in this thread (typically
     * consumed from the <code>TaskScheduler</code>), or <code>null</code>
     * meaning that it's running work on behalf of the kernel.
     *
     * @return the current owner, or <code>null</code> if owned by the kernel
     */
    TaskOwner getCurrentOwner() {
        return currentOwner;
    }

    /**
     * Sets the current owner of the work being done by this thread. The only
     * components who have access to this ability are those in the kernel and
     * the <code>TaskScheduler</code> (via the <code>TaskHandler</code>).
     * Because the latter only allows setting the owner for a task in the
     * current thread of execution, only kernel components have the ability
     * to set the ownership of one thread from within a different thread
     * of control, which should never be done.
     *
     * @param owner the new owner, or <code>null</code> for the kernel
     */
    void setCurrentOwner(TaskOwner owner) {
        currentOwner = owner;
        ContextResolver.
            setContext((AbstractKernelAppContext)(owner.getContext()));
    }

    /**
     * Tells this thread to start running a new task. Unlike the short-lived
     * <code>KernelRunnable</code>s passed through the scheduler, this
     * method uses a <code>Runnable</code> that represents a long-lived
     * task. The <code>Runnable</code> should not throw any exceptions,
     * and when its <code>run</code> method returns the thread will be made
     * available for other work (i.e., no re-tries are attempted). Note
     * that this method will fail if the thread isn't yet running, or is
     * currently executing another task.
     *
     * @param task a <code>Runnable</code> to run in this thread
     * @param initialOwner the initial <code>TaskOwner</code for this
     *                     task, which is <code>null</code> if the owner
     *                     is the kernel
     *
     * @throws IllegalStateException if this thread isn't currently running,
     *                               has been requested to shut down, or
     *                               or if a task is already being run
     */
    synchronized void runTask(Runnable task, TaskOwner initialOwner) {
        // make sure that the thread is running but idle
        if (! running)
            throw new IllegalStateException("this thread is not running");
        if (! waiting)
            throw new IllegalStateException("a task is already running");

        // remember what our next task is and change the owner
        nextTask = task;
        currentOwner = initialOwner;

        // notify the thrad to wake and process the new task
        notify();
    }

    /**
     * Runs this thread, sleeping until a new task is ready, running that
     * task, and then returning to sleep to wait for the next task. On
     * completion of each task the <code>ResourceCoordinator</code> will
     * be notified that this thread is ready for more work.
     */
    public void run() {
        running = true;
        waiting = true;
        while (running) {
            // wait for the next task
            while (nextTask == null) {
                try {
                    synchronized(this) {
                        wait();
                    }
                } catch (InterruptedException ie) {
                    // FIXME: do I actually want to do something here?
                }

                // if we woke up becuase we got shut down, then return
                if (! running)
                    return;
            }

            // run the task
            waiting = false;
            nextTask.run();
            nextTask = null;
            waiting = true;

            // notify that we're ready to consume another task
            if (running)
                resourceCoordinator.notifyThreadWaiting(this);
        }
    }

    /**
     * Tells this thread to finish running after the current task (if any)
     * completes. Once this has been called no further tasks will be accepted
     * by this thread, and when the current task has completed this thread
     * of execution will end.
     */
    synchronized void shutdown() {
        running = false;
        notify();
    }

}
