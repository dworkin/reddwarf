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
 * This interface is used only to manager the current owner of this
 * thread, which starts as the owner of the initial task, but will be
 * re-set with each task run out of the scheduler. See
 * <code>TaskHandler</code> for details on this process.
 *
 * @since 1.0
 * @author Seth Proctor
 */
abstract class TaskThread extends Thread {

    // the current owner of this thread
    private TaskOwner currentOwner;

    /**
     * Creates a new instance of <code>TaskThread</code>.
     *
     * @param r the root <code>Runnable</code> for this <code>Thread</code>
     */
    protected TaskThread(Runnable r) {
        super(r);
        currentOwner = Kernel.TASK_OWNER;
    }


    /**
     * Returns a reference to the currently executing thread object.
     * 
     * @return the currently executing thread.
     */
    public static TaskThread currentThread() {
	return (TaskThread) Thread.currentThread();
    }

    /**
     * Returns the current owner of the work being done by this thread.
     * Depending on what is currently running in this thread, the owner may
     * be the owner of the last <code>Runnable</code> provided to
     * <code>runTask</code>, or the owner of a specific
     * <code>KernelRunnable</code> running in this thread (typically
     * consumed from the <code>TaskScheduler</code>).
     *
     * @return the current owner
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
     * @param owner the new owner
     */
    void setCurrentOwner(TaskOwner owner) {
        currentOwner = owner;
        ContextResolver.
            setContext((AbstractKernelAppContext)(owner.getContext()));
    }

}
