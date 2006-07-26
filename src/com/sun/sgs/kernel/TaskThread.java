
package com.sun.sgs.kernel;


/**
 * This abstract class is the specific implementation of
 * <code>Thread</code> that runs all tasks in the system. It maintains
 * local state about the task, and implements the logic for running a
 * task in the server.
 * <p>
 * FIXME: This currently is a very naive implementation of task
 * management...it's enough for initial tests, but needs to be re-built
 * for the actual system.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class TaskThread extends Thread
{

    // the Task that this thread is currently executing
    private Task task;

    /**
     * Creates an instance of <code>TaskThread</code>.
     */
    public TaskThread() {
        task = null;
    }

    /**
     * Returns the task currently associated with this <code>Thread</code>.
     *
     * @return the current <code>Task</code>
     */
    public Task getTask() {
        return task;
    }

    /**
     * Tells this thread to start executing the given <code>Task</code>.
     *
     * @param task the <code>Task</code> to run
     */
    public void executeTask(Task task) {
        // first, check that we're not already running a task
        // FIXME: we should decide if tasks get queued up here, or
        // whether this is in fact an error
        if (this.task != null) {
            System.err.println("Tried to execute on already running task");
            return;
        }

        // next, remember the new task
        this.task = task;

        // finally, start execution by waking up the run loop and
        // running the new task
        synchronized(this) {
            notify();
        }
    }

    /**
     * Runs this thread.
     */
    public void run() {
        while (true) {
            try {
                synchronized(this) {
                    wait();
                }
            } catch (InterruptedException ie) {
                System.err.println("Task thread was interrupted");
            }

            if (task != null) {
                task.run();
                task = null;
            }
        }
    }

}
