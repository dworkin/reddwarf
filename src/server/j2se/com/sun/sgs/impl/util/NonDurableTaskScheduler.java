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
     * scheduler and transaction proxy.  For tasks scheduled using the
     * methods of this class:
     *
     * <p>The task owner for tasks scheduled using the {@link
     * #scheduleTask scheduleTask} method is the owner returned by
     * invoking {@link TransactionProxy#getCurrentOwner
     * getCurrentOwner} on the specified proxy.
     *
     * <p>The task service used to schedule non-durable,
     * non-transactionl tasks (using the {@link
     * #scheduleNonTransactionalTask scheduleNonTransactionalTask}
     * method) is obtained by invoking the {@link
     * TransactionProxy#getService getService} method with the {@link
     * TaskService} class object on the specified proxy.
     */
    public NonDurableTaskScheduler(
	TaskScheduler taskScheduler, TransactionProxy proxy)
    {
	if (taskScheduler == null || proxy == null) {
	    throw new NullPointerException("null argument");
	}
	this.taskScheduler = taskScheduler;
	this.owner = proxy.getCurrentOwner();
	this.taskService = proxy.getService(TaskService.class);
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
     * service obtained during construction.
     *
     * @param task a task
     */
    public void scheduleNonTransactionalTask(KernelRunnable task) {
	taskService.scheduleNonDurableTask(task);
    }
}

    
