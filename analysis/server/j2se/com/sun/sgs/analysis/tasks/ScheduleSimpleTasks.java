package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import java.io.Serializable;
import java.util.Properties;

/**
 * A task that schedules a number of simple tasks, each of which runs a number
 * of times.  When done, prints performance information and exits.  This test
 * provides a simple metric for the performance of the task service.  By
 * default, the number of tasks scheduled equals the number of available
 * processors, but the value can be specified with the {@value #TASKS_KEY}
 * configuration property.  The number of operations per task can be specified
 * with the {@value #OPS_KEY} property, and defaults to {@value #DEFAULT_OPS}.
 */
public class ScheduleSimpleTasks implements Task, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The configuration property for the number of tasks to run. */
    public static final String TASKS_KEY =
	ScheduleSimpleTasks.class.getName() + ".tasks";

    /** The configuration property for the number of operations per task. */
    public static final String OPS_KEY =
	ScheduleSimpleTasks.class.getName() + ".ops";

    /** The default number of operations per task. */
    public static final int DEFAULT_OPS = 1000;

    /** The number of tasks. */
    private final int tasks;

    /** The number of operations per task. */
    private final int ops;

    /**
     * Creates an instance using the specified configuration properties.
     *
     * @param properties the configuration properties
     */
    public ScheduleSimpleTasks(Properties properties) {
	String tasksString = properties.getProperty(TASKS_KEY);
	tasks = (tasksString == null)
	    ? Runtime.getRuntime().availableProcessors()
	    : Integer.parseInt(tasksString);
	String opsString = properties.getProperty(OPS_KEY);
	ops = (opsString == null) ? DEFAULT_OPS : Integer.parseInt(opsString);
    }

    /** Schedules the tasks. */
    public void run() {
	System.out.println(
	    "Starting " + tasks + " tasks, " + ops + " ops per task");
	TaskManager taskManager = AppContext.getTaskManager();
	Status status = new Status(tasks, ops);
	for (int i = 0; i < tasks; i++) {
	    taskManager.scheduleTask(new SimpleTask(status, ops));
	}
    }

    /** A managed object that tracks the status of the tasks. */
    private static class Status implements ManagedObject, Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The time the tasks were started. */
	private final long startTime = System.currentTimeMillis();

	/** The number of tasks. */
	private final int tasks;

	/** The number of tasks that have not completed. */
	private int remainingTasks;

	/** The total number of operations being run. */
	private final int totalOps;

	/**
	 * Creates an instance.
	 *
	 * @param tasks the number of tasks
	 * @param ops the number of operations per task
	 */
	Status(int tasks, int ops) {
	    this.tasks = tasks;
	    remainingTasks = tasks;
	    totalOps = tasks * ops;
	}

	/** Notes that a task has completed its operations. */
	void taskDone() {
	    AppContext.getDataManager().markForUpdate(this);
	    remainingTasks--;
	    if (remainingTasks == 0) {
		long elapsedTime = System.currentTimeMillis() - startTime;
		System.err.println("Tasks completed");
		System.err.println("Elapsed time: " + elapsedTime + " ms");
		System.err.println("Tasks: " + tasks);
		System.err.println("Total ops: " + totalOps);
		System.err.println("Ops/sec: " +
				   ((totalOps * 1000) / elapsedTime));
		System.err.println("Elapsed time per op: " +
				   ((elapsedTime * tasks) / totalOps) + " ms");
		System.exit(0);
	    }
	}
    }

    /**
     * A task that runs a specified number of times, and then notifies a
     * status object that it is done.
     */
    private static class SimpleTask implements Serializable, Task {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** A reference to the status object to notify. */
	private final ManagedReference status;

	/** The remaining number of operations to run. */
	private int remainingOps;

	/**
	 * Creates an instance.
	 *
	 * @param status the status object to notify when done
	 * @param ops the number of operations to run
	 */
	SimpleTask(Status status, int ops) {
	    this.status = AppContext.getDataManager().createReference(status);
	    remainingOps = ops;
	}

	/** Notifies the status object if done, else reschedules itself. */
	public void run() {
	    remainingOps--;
	    if (remainingOps == 0) {
		status.get(Status.class).taskDone();
	    } else {
		AppContext.getTaskManager().scheduleTask(this);
	    }
	}
    }
}
