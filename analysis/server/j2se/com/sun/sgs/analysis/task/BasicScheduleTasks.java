package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import java.io.Serializable;
import java.util.Properties;

/**
 * An abstract task class that provides support for scheduling a number of
 * tasks, printing performance information after each has run a number of
 * times, and optionally repeating the test.  By default, the number of tasks
 * scheduled equals the number of available processors, but the value can be
 * specified with the {@value #TASKS_KEY} configuration property.  The total
 * number of times the tasks are run can be specified with the {@value
 * #TOTAL_COUNT_KEY} property, and defaults to {@value #DEFAULT_TOTAL_COUNT}.
 * The test can be repeated by setting the {@value #REPEAT_KEY} property to the
 * desired number of repetitions.
 */
public abstract class BasicScheduleTasks
    implements ManagedObject, Task, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The configuration property for how many times to perform the test. */
    public static final String REPEAT_KEY =
	BasicScheduleTasks.class.getName() + ".repeat";

    /** The configuration property for the number of tasks to run. */
    public static final String TASKS_KEY =
	BasicScheduleTasks.class.getName() + ".tasks";

    /**
     * The configuration property for the total number times to run the
     * tasks.
     */
    public static final String TOTAL_COUNT_KEY =
	BasicScheduleTasks.class.getName() + ".total.count";

    /** The default total number of times to the tasks. */
    public static final int DEFAULT_TOTAL_COUNT = 10000;

    /** The number of times to repeat the test. */
    protected final int repeat;

    /** The number of tasks. */
    protected final int tasks;

    /** The number of times to run each task. */
    protected final int count;

    /** The number of the current run of the test. */
    protected int repetition = 0;

    /** The number of tasks that have not completed for the current run. */
    protected int remainingTasks;

    /** The time the tasks were started for the current run. */
    protected long startTime;

    /**
     * Creates an instance using the specified configuration properties.
     *
     * @param properties the configuration properties
     */
    protected BasicScheduleTasks(Properties properties) {
	repeat = Integer.parseInt(properties.getProperty(REPEAT_KEY, "1"));
	tasks = Integer.parseInt(
	    properties.getProperty(
		TASKS_KEY,
		String.valueOf(Runtime.getRuntime().availableProcessors())));
	int totalCount = Integer.parseInt(
	    properties.getProperty(
		TOTAL_COUNT_KEY, String.valueOf(DEFAULT_TOTAL_COUNT)));
	count = totalCount / tasks;
    }

    /** Schedules the tasks. */
    public void run() {
	AppContext.getDataManager().markForUpdate(this);
	repetition++;
	if (repetition == 1) {
	    System.out.println(
		"Starting " + (repeat > 0 ? (repeat + " repetitions, ") : "") +
		tasks + " tasks, " + count + " runs per task");
	}
	remainingTasks = tasks;
	startTime = System.currentTimeMillis();
	AppContext.getTaskManager().scheduleTask(
	    new ScheduleTasks(this, tasks, count));
    }

    /** Creates a task to run. */
    protected abstract Task createTask();

    /** A task that schedules a number of simple tasks. */
    private static class ScheduleTasks implements Serializable, Task {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** A reference to the object to notify when done. */
	private final ManagedReference schedulerRef;
	
	/** How many tasks to schedule. */
	private int tasks;

	/** How many times to run each task. */
	private final int count;

	ScheduleTasks(BasicScheduleTasks scheduler, int tasks, int count) {
	    schedulerRef =
		AppContext.getDataManager().createReference(scheduler);
	    this.tasks = tasks;
	    this.count = count;
	}

	public void run() {
	    TaskManager taskManager = AppContext.getTaskManager();
	    BasicScheduleTasks scheduler =
		schedulerRef.get(BasicScheduleTasks.class);
	    taskManager.scheduleTask(scheduler.createTask());
	    tasks--;
	    if (tasks > 0) {
		taskManager.scheduleTask(this);
	    }
	}
    }

    /** Notes that a task has completed its operations. */
    protected void taskDone() {
	AppContext.getDataManager().markForUpdate(this);
	remainingTasks--;
	if (remainingTasks == 0) {
	    long elapsedTime = System.currentTimeMillis() - startTime;
	    int totalRuns = count * tasks;
	    System.err.println(repeat > 0
			       ? ("Results for repetition " + repetition + ":")
			       : "Results:");
	    System.err.println("Tasks: " + tasks);
	    System.err.println("Total runs: " + totalRuns);
	    System.err.println("Elapsed time: " + elapsedTime + " ms");
	    System.err.println("Runs/sec: " +
			       ((totalRuns * 1000) / elapsedTime));
	    System.err.println("Elapsed time per run: " +
			       ((elapsedTime * tasks) / totalRuns) + " ms");
	    if (repetition < repeat) {
		AppContext.getTaskManager().scheduleTask(this);
	    } else {
		System.exit(0);
	    }
	}
    }
}
