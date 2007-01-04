package com.sun.sgs.test.util;

import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;

public class DummyTaskService implements TaskService {

    public String getName() {
	return toString();
    }

    public void configure(ComponentRegistry registry, TransactionProxy proxy) {
    }

    public PeriodicTaskHandle schedulePeriodicTask(
						   Task task, long delay, long period)
    {
	throw new AssertionError("Not implemented");
    }

    public void scheduleTask(Task task) {
	try {
	    task.run();
	} catch (Exception e) {
	    System.err.println(
			       "DummyTaskService.scheduleTask exception: " + e);
	    e.printStackTrace();
	    throw new RuntimeException(e);
	}
    }

    public void scheduleTask(Task task, long delay) {
	scheduleTask(task);
    }
	
    public void scheduleNonDurableTask(KernelRunnable task) {
	try {
	    task.run();
	} catch (Exception e) {
	    System.err.println(
			       "DummyTaskService.scheduleNonDurableTask exception: " + e);
	    e.printStackTrace();
	    throw new RuntimeException(e);
	}
    }
	
    public void scheduleNonDurableTask(KernelRunnable task, long delay) {
	scheduleNonDurableTask(task);
    }
    public void scheduleNonDurableTask(KernelRunnable task,
				       Priority priority)
    {
	scheduleNonDurableTask(task);
    }
}
