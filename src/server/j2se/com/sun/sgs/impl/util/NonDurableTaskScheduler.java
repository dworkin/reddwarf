package com.sun.sgs.impl.util;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
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
     * Constructs an instance of this class with the specified task
     * scheduler, task owner, and task service.
     *
     * @param taskScheduler a task scheduler
     * @param proxy a transaction proxy
     * @param taskService a task service
     */
    public NonDurableTaskScheduler(
	TaskScheduler taskScheduler,
        TaskOwner owner,
        TaskService taskService)
    {
	if (taskScheduler == null || owner == null || taskService == null) {
	    throw new NullPointerException("null argument");
	}
	this.taskScheduler = taskScheduler;
	this.owner = owner;
	this.taskService = taskService;
    }

    /**
     * Schedules a non-durable, transactional task using the task
     * scheduler and task owner specified during construction.
     *
     * @param task a task
     */
    public void scheduleTask(KernelRunnable task) {
	taskScheduler.scheduleTask(new TransactionRunner(task), owner);
    }

    /**
     * Schedules a non-durable, non-transactional task using the task
     * scheduler and task owner specified during construction.
     *
     * @param task a task
     */
    public void scheduleNonTransactionalTask(KernelRunnable task) {
	taskScheduler.scheduleTask(task, owner);
    }
    
    /**
     * Schedules a non-durable, non-transactional task using the task
     * service specified during construction.
     *
     * @param task a task
     */
    public void scheduleNonTransactionalTaskOnCommit(KernelRunnable task) {
	taskService.scheduleNonDurableTask(task);
    }
}

    
