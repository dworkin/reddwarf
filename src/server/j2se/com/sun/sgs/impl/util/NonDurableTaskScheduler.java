package com.sun.sgs.impl.util;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionRunner;

/**
 * Utility class for scheduling non-durable tasks to run either inside
 * or outside of a transaction.
 */
public class NonDurableTaskScheduler {

    private final TaskOwner owner;
    private final TaskScheduler taskScheduler;
    private final TaskService taskService;

    /**
     * Constructs an instance of this class with the specified
     * task owner, (non-transactional) task scheduler, and
     * (transactional) task service.
     */
    public NonDurableTaskScheduler(
	TaskScheduler taskScheduler,
        TaskOwner owner,
        TaskService taskService)
    {
	if (taskScheduler == null || owner == null || taskService == null) {
	    throw new NullPointerException("null argument");
	}
        this.owner = owner;
	this.taskScheduler = taskScheduler;
	this.taskService = taskService;
    }

    /**
     * Schedules a non-durable, transactional task using the task
     * scheduler and task owner obtained during construction.
     *
     * @param task a task
     */
    public void scheduleTask(KernelRunnable task) {
	taskScheduler.scheduleTask(new TransactionRunner(task), owner);
    }

    /**
     * Schedules a non-durable, non-transactional task using the task
     * scheduler and task owner obtained during construction.
     *
     * @param task a task
     */
    public void scheduleNonTransactionalTask(KernelRunnable task) {
        taskScheduler.scheduleTask(task, owner);
    }

    /**
     * Schedules a non-durable, non-transactional task using the task
     * service obtained during construction.
     *
     * @param task a task
     */
    public void scheduleNonTransactionalTaskUsingService(KernelRunnable task) {
	taskService.scheduleNonDurableTask(task);
    }
}

    
