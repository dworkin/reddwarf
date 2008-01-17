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
 * tasks and printing performance information after each has run a number of
 * times.  By default, the number of tasks scheduled equals the number of
 * available processors, but the value can be specified with the {@value
 * #TASKS_KEY} configuration property.  The total number of times the tasks are
 * run can be specified with the {@value #TOTAL_COUNT_KEY} property, and
 * defaults to {@value #DEFAULT_TOTAL_COUNT}.
 */
public abstract class BasicScheduleTasks
    implements ManagedObject, Task, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The configuration property for the number of tasks to run. */
    public static final String TASKS_KEY =
	"com.sun.sgs.analysis.task.BasicScheduleTasks.tasks";

    /**
     * The configuration property for the total number times to run the
     * tasks.
     */
    public static final String TOTAL_COUNT_KEY =
	"com.sun.sgs.analysis.task.BasicScheduleTasks.total.count";

    /** The default total number of times to the tasks. */
    public static final int DEFAULT_TOTAL_COUNT = 10000;

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

    /** A task that schedules a number of tasks. */
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

    /**
     * Creates an instance using the specified configuration properties.
     *
     * @param properties the configuration properties
     */
    protected BasicScheduleTasks(Properties properties) {
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
		"Starting " + tasks + " tasks, " + count + " runs per task");
	}
	remainingTasks = tasks;
	startTime = System.currentTimeMillis();
	AppContext.getTaskManager().scheduleTask(
	    new ScheduleTasks(this, tasks, count));
    }

    /** Creates a task to run. */
    protected abstract Task createTask();

    /** Notes that a task has completed its operations. */
    protected void taskDone() {
	AppContext.getDataManager().markForUpdate(this);
	remainingTasks--;
	if (remainingTasks == 0) {
	    long elapsedTime = System.currentTimeMillis() - startTime;
	    int totalRuns = count * tasks;
	    System.err.println("Repetition " + repetition + ":");
	    System.err.println(
		"  " + tasks + " tasks, " +
		totalRuns + " txns, " +
		elapsedTime + " ms, " +
		((totalRuns * 1000) / elapsedTime) + " txn/sec");
	    AppContext.getTaskManager().scheduleTask(this);
	}
    }
}
