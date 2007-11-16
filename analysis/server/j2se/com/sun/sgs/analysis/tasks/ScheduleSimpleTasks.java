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
public class ScheduleSimpleTasks implements ManagedObject, Task, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The configuration property for how many times to perform the test. */
    public static final String REPEAT_KEY =
	ScheduleSimpleTasks.class.getName() + ".repeat";

    /** The configuration property for the number of tasks to run. */
    public static final String TASKS_KEY =
	ScheduleSimpleTasks.class.getName() + ".tasks";

    /** The configuration property for the number of operations per task. */
    public static final String OPS_KEY =
	ScheduleSimpleTasks.class.getName() + ".ops";

    /** The default number of operations per task. */
    public static final int DEFAULT_OPS = 1000;

    /** The number of times to repeat the test. */
    private final int repeat;

    /** The number of tasks. */
    private final int tasks;

    /** The number of operations per task. */
    private final int ops;

    /** The number of the current run of the test. */
    private int repetition = 0;

    /** The number of tasks that have not completed. */
    private int remainingTasks;

    /** The time the tasks were started. */
    private long startTime;

    /**
     * Creates an instance using the specified configuration properties.
     *
     * @param properties the configuration properties
     */
    public ScheduleSimpleTasks(Properties properties) {
	String repeatString = properties.getProperty(REPEAT_KEY);
	repeat = (repeatString == null) ? 1 : Integer.parseInt(repeatString);
	String tasksString = properties.getProperty(TASKS_KEY);
	tasks = (tasksString == null)
	    ? Runtime.getRuntime().availableProcessors()
	    : Integer.parseInt(tasksString);
	String opsString = properties.getProperty(OPS_KEY);
	ops = (opsString == null) ? DEFAULT_OPS : Integer.parseInt(opsString);
    }

    /** Schedules the tasks. */
    public void run() {
	AppContext.getDataManager().markForUpdate(this);
	repetition++;
	if (repetition == 1) {
	    System.out.println(
		"Starting " + (repeat > 0 ? (repeat + " repetitions, ") : "") +
		tasks + " tasks, " + ops + " ops per task");
	}
	remainingTasks = tasks;
	startTime = System.currentTimeMillis();
	TaskManager taskManager = AppContext.getTaskManager();
	for (int i = 0; i < tasks; i++) {
	    taskManager.scheduleTask(new SimpleTask(this, ops));
	}
    }

    /** Notes that a task has completed its operations. */
    void taskDone() {
	AppContext.getDataManager().markForUpdate(this);
	remainingTasks--;
	if (remainingTasks == 0) {
	    long elapsedTime = System.currentTimeMillis() - startTime;
	    int totalOps = ops * tasks;
	    System.err.println(repeat > 0
			       ? ("Results for repetition " + repetition + ":")
			       : "Results:");
	    System.err.println("Tasks: " + tasks);
	    System.err.println("Total ops: " + totalOps);
	    System.err.println("Elapsed time: " + elapsedTime + " ms");
	    System.err.println("Ops/sec: " +
			       ((totalOps * 1000) / elapsedTime));
	    System.err.println("Elapsed time per op: " +
			       ((elapsedTime * tasks) / totalOps) + " ms");
	    if (repetition < repeat) {
		AppContext.getTaskManager().scheduleTask(this);
	    } else {
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

	/** A reference to the object to notify when done. */
	private final ManagedReference scheduler;

	/** The remaining number of operations to run. */
	private int remainingOps;

	/**
	 * Creates an instance.
	 *
	 * @param status the status object to notify when done
	 * @param ops the number of operations to run
	 */
	SimpleTask(ScheduleSimpleTasks scheduler, int ops) {
	    this.scheduler =
		AppContext.getDataManager().createReference(scheduler);
	    remainingOps = ops;
	}

	/** Notifies the status object if done, else reschedules itself. */
	public void run() {
	    remainingOps--;
	    if (remainingOps == 0) {
		scheduler.get(ScheduleSimpleTasks.class).taskDone();
	    } else {
		AppContext.getTaskManager().scheduleTask(this);
	    }
	}
    }
}
