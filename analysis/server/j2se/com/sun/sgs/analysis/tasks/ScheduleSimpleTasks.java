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
 * of times, optionally allocating a new task object for each run.  When done,
 * prints performance information and exits.  This test provides a simple
 * metric for the performance of the task service.  By default, the number of
 * tasks scheduled equals the number of available processors, but the value can
 * be specified with the {@value #TASKS_KEY} configuration property.  The
 * number of operations per task can be specified with the {@value #COUNT_KEY}
 * property, and defaults to {@value #DEFAULT_COUNT}.  Allocates a new object
 * for each task run if the {@value #ALLOCATE_KEY} configuration property is
 * set to {@code true}.
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

    /** The configuration property for the number times to run each task. */
    public static final String COUNT_KEY =
	ScheduleSimpleTasks.class.getName() + ".count";

    /** The default number of times to run each task. */
    public static final int DEFAULT_COUNT = 1000;

    /**
     * The configuration property for whether to create a new managed object
     * for each task run.
     */
    public static final String ALLOCATE_KEY =
	ScheduleSimpleTasks.class.getName() + ".allocate";

    /** The number of times to repeat the test. */
    private final int repeat;

    /** The number of tasks. */
    private final int tasks;

    /** The number of times to run each task. */
    private final int count;

    /** Whether to allocate new tasks as managed objects. */
    private final boolean allocate;

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
	repeat = Integer.parseInt(properties.getProperty(REPEAT_KEY, "1"));
	tasks = Integer.parseInt(
	    properties.getProperty(
		TASKS_KEY,
		String.valueOf(Runtime.getRuntime().availableProcessors())));
	count = Integer.parseInt(
	    properties.getProperty(COUNT_KEY, String.valueOf(DEFAULT_COUNT)));
	allocate = Boolean.valueOf(
	    properties.getProperty(ALLOCATE_KEY, "false"));
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
	    new ScheduleTasks(this, tasks, count, allocate));
    }

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

	/** Whether to allocate tasks as managed objects. */
	private final boolean allocate;

	ScheduleTasks(ScheduleSimpleTasks scheduler,
		      int tasks,
		      int count,
		      boolean allocate)
	{
	    schedulerRef =
		AppContext.getDataManager().createReference(scheduler);
	    this.tasks = tasks;
	    this.count = count;
	    this.allocate = allocate;
	}

	public void run() {
	    TaskManager taskManager = AppContext.getTaskManager();
	    ScheduleSimpleTasks scheduler =
		schedulerRef.get(ScheduleSimpleTasks.class);
	    taskManager.scheduleTask(
		allocate ? new ManagedSimpleTask(scheduler, count)
		: new SimpleTask(scheduler, count));
	    tasks--;
	    if (tasks > 0) {
		taskManager.scheduleTask(this);
	    }
	}
    }

    /** Notes that a task has completed its operations. */
    void taskDone() {
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

    /**
     * A task that runs a specified number of times, and then notifies a
     * status object that it is done.
     */
    private static class SimpleTask implements Serializable, Task {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** A reference to the object to notify when done. */
	final ManagedReference schedulerRef;

	/** The remaining number of operations to run. */
	int count;

	/**
	 * Creates an instance.
	 *
	 * @param status the status object to notify when done
	 * @param count the number times to run this task
	 */
	SimpleTask(ScheduleSimpleTasks scheduler, int count) {
	    schedulerRef =
		AppContext.getDataManager().createReference(scheduler);
	    this.count = count;
	}

	/** Notifies the status object if done, else reschedules itself. */
	public void run() {
	    count--;
	    if (count == 0) {
		schedulerRef.get(ScheduleSimpleTasks.class).taskDone();
	    } else {
		AppContext.getTaskManager().scheduleTask(getNextTask());
	    }
	}

	/** Returns the task to schedule next. */
	Task getNextTask() {
	    return this;
	}
    }

    /**
     * A task that is a managed object and that schedules a new managed object
     * for each task run.
     */
    private static class ManagedSimpleTask extends SimpleTask
	implements ManagedObject
    {
	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/**
	 * Creates an instance.
	 *
	 * @param status the status object to notify when done
	 * @param count the number times to run this task
	 */
	ManagedSimpleTask(ScheduleSimpleTasks scheduler, int count) {
	    super(scheduler, count);
	}

	Task getNextTask() {
	    return new ManagedSimpleTask(
		schedulerRef.get(ScheduleSimpleTasks.class), count);
	}
    }
}
