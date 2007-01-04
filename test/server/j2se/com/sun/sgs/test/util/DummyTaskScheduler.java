package com.sun.sgs.test.util;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;
import java.util.Collection;

public class DummyTaskScheduler implements TaskScheduler {

    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner) {
	throw new AssertionError("Not implemented");
    }

    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
				       Priority priority)
    {
	throw new AssertionError("Not implemented");
    }

    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
				       long startTime)
    {
	throw new AssertionError("Not implemented");
    }

    public TaskReservation reserveTasks(Collection<? extends KernelRunnable>
					tasks, TaskOwner owner)
    {
	throw new AssertionError("Not implemented");
    }

    public void scheduleTask(KernelRunnable task, TaskOwner owner) {
	try {
	    task.run();
	} catch (Exception e) {
	    System.err.println(
			       "DummyTaskScheduler.scheduleTask exception: " + e);
	    e.printStackTrace();
	    throw new RuntimeException(e);
	}
    }

    public void scheduleTask(KernelRunnable task, TaskOwner owner,
			     Priority priority)
    {
	scheduleTask(task, owner);
    }

    public void scheduleTask(KernelRunnable task, TaskOwner owner,
			     long startTime)
    {
	scheduleTask(task, owner);
    }

    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
						     TaskOwner owner,
						     long startTime,
						     long period)
    {
	throw new AssertionError("Not implemented");
    }

	
}
