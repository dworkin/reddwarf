
/*
 * TaskThread.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Jul  6, 2006	 5:27:12 PM
 * Desc: 
 *
 */

package com.sun.sgs.manager;


/**
 * This package-private class is the specific implementation of
 * <code>Thread</code> that runs all tasks in the system. It maintains
 * local state about the task, and implements the logic for running a
 * task in the server.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
class TaskThread extends Thread
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

        // next, remember the new task

        // finally, start execution by waking up the run loop and
        // running the new task
    }

    /**
     * Runs this thread.
     */
    public void run() {
        // This needs to be over-ridden to provide common behavior
    }

}
