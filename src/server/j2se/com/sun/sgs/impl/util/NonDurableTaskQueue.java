/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.KernelRunnable;
import java.util.LinkedList;


/**
 * Utility class for scheduling non-durable, transactional tasks to be
 * run in succession.  Tasks are added to the queue from a
 * non-transactional context.  This implementation is thread-safe.
 */
public class NonDurableTaskQueue {

    /** The task scheduler. */
    private final NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The identity for tasks. */
    private final Identity identity;

    /** The lock for accessing state. */
    private final Object lock = new Object();

    /** The queue of tasks to run. */
    private final LinkedList<KernelRunnable> tasks =
	new LinkedList<KernelRunnable>();

    /** The task to process the head of the queue. */
    private ProcessQueueTask processQueueTask = null;

    /**
     * Constructs a {@code NonDurableTaskQueue} with the given {@code
     * scheduler} and {@code identity}.
     *
     * @param	scheduler a {@code NonDurableTaskScheduler}
     * @param	identity an identity
     */
    public NonDurableTaskQueue(
	NonDurableTaskScheduler scheduler,
	Identity identity)		       
    {
	if (scheduler == null || identity == null) {
	    throw new NullPointerException("null argument");
	}
	this.nonDurableTaskScheduler = scheduler;
	this.identity = identity;
    }

    /**
     * Adds to this task queue a {@code task} that is scheduled using
     * the {@code NonDurableTaskScheduler} and {@code identity}
     * specified during construction.  The given {@code task} will be
     * run after all preceeding tasks either successfully complete, or fail
     * with a non-retryable exception.
     *
     * @param	task a task
     */
    public void addTask(KernelRunnable task) {
	if (task == null) {
	    throw new NullPointerException("null task");
	}
	synchronized (lock) {
	    tasks.add(task);
	    if (processQueueTask == null) {
		processQueueTask = new ProcessQueueTask();
		nonDurableTaskScheduler.
		    scheduleTask(processQueueTask, identity);
	    }
	}
    }

    /**
     * Task for processing the head of the task queue.
     *
     * <p>This {@code ProcessQueueTask} invokes the {@code run} method
     * on the task at the head of the queue.  If that task
     * successfully completes or throws a non-retryable exception, the
     * task is removed from the queue, and if the queue is non-empty
     * after removing the processed task, this {@code
     * ProcessQueueTask} reschedules itself to process the next task
     * at the head of the queue.  If the task throws a retryable
     * exception, that task remains at the head of the queue since the
     * task will be retried by the scheduler.
     */
    private class ProcessQueueTask implements KernelRunnable {

	/** {@inheritDoc} */
	public void run() throws Exception {

	    Exception exception = null;
	    
	    try {
		KernelRunnable task;
		synchronized (lock) {
		    task = tasks.peek();
		}
		task.run();
		
	    } catch (Exception e) {
		exception = e;
		throw e;
		
	    } finally {
		if (! isRetryable(exception)) {
		    synchronized (lock) {
			tasks.remove();
			if (tasks.isEmpty()) {
			    processQueueTask = null;
			} else {
			    nonDurableTaskScheduler.
				scheduleTask(this, identity);
			}
		    }
		}
	    }
	}
    }

    /**
     * Returns {@code true} if the given {@code Throwable} is a
     * "retryable" exception, meaning that it implements {@code
     * ExceptionRetryStatus}, and invoking its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * {@code true}.
     *
     * @param	t   a throwable
     */
    private static boolean isRetryable(Throwable t) {
	return
	    t instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) t).shouldRetry();
    }
}
