package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.util.ScalableHashMap;
import java.io.Serializable;
import java.util.Properties;

/**
 * A task that creates a {@link ScalableHashMap}, and schedules a number of
 * {@link MapPutTask}s to insert random integers into it.  By default, the
 * number of tasks scheduled equals the number of available processors, as
 * returned by {@link Runtime#availableProcessors Runtime.availableProcessors},
 * but the value can be specified with the {@value TASKS_KEY} system property.
 * By default runs each task {@value #DEFAULT_COUNT} times and then exits, but
 * the number of times each task should be run can be specified with the
 * {@value #COUNT_KEY} configuration property.
 */
public class ScheduleMapPutsTask implements ManagedObject, Serializable, Task {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The configuration property for how many times to perform the test. */
    public static final String REPEAT_KEY =
	ScheduleMapPutsTask.class.getName() + ".repeat";

    /** The configuration property for the number of tasks to run. */
    public static final String TASKS_KEY =
	ScheduleMapPutsTask.class.getName() + ".tasks";

    /**
     * The configuration property for the number of times to schedule each
     * task.
     */
    public static final String COUNT_KEY =
	ScheduleMapPutsTask.class.getName() + ".count";

    /** The default number of times to schedule each task. */
    public static final int DEFAULT_COUNT = 1000;

    /** The number of times to repeat the test. */
    private final int repeat;

    /** The number of tasks to run. */
    private final int tasks;

    /** The number of times to schedule each task. */
    private final int count;

    /** The number of the current run of the test. */
    private int repetition = 0;

    /** The number of tasks that have not completed. */
    private int remainingTasks;

    /** The time the tasks were started. */
    private long startTime;

    /**
     * Creates an instance of this class using the specified configuration
     * properties.
     */
    public ScheduleMapPutsTask(Properties properties) { 
	String repeatString = properties.getProperty(REPEAT_KEY);
	repeat = (repeatString == null) ? 1 : Integer.parseInt(repeatString);
	String tasksString = properties.getProperty(TASKS_KEY);
	if (tasksString == null) {
	    tasks = Runtime.getRuntime().availableProcessors();
	} else {
	    tasks = Integer.parseInt(tasksString);
	}
	String countString = properties.getProperty(COUNT_KEY);
	count = (countString == null)
	    ? DEFAULT_COUNT : Integer.parseInt(countString);
    }

    /** Schedules the tasks. */
    public void run() {
	AppContext.getDataManager().markForUpdate(this);
	repetition++;
	if (repetition == 1) {
	    System.out.println(
		"Starting " + (repeat > 0 ? (repeat + " repetitions, ") : "") +
		tasks + " tasks, " + count + " times per task");
	}
	remainingTasks = tasks;
	startTime = System.currentTimeMillis();
	AppContext.getTaskManager().scheduleTask(
	    new ScheduleTasks(new ScalableHashMap<Integer, Integer>(),
			      this, tasks, count));
    }

    /** A task that schedules a number of MapPutTasks. */
    private static class ScheduleTasks implements Serializable, Task {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** A reference to the map. */
	private final ManagedReference mapRef;

	/** A reference to the object to notify when done. */
	private final ManagedReference schedulerRef;
	
	/** How many tasks to schedule. */
	private int tasks;

	/** How many times to run each task. */
	private final int count;

	ScheduleTasks(ScalableHashMap<Integer, Integer> map,
		      ScheduleMapPutsTask scheduler,
		      int tasks,
		      int count)
	{
	    DataManager dataManager = AppContext.getDataManager();
	    mapRef = dataManager.createReference(map);
	    schedulerRef = dataManager.createReference(scheduler);
	    this.tasks = tasks;
	    this.count = count;
	}

	public void run() {
	    TaskManager taskManager = AppContext.getTaskManager();
	    taskManager.scheduleTask(
		new MapPutTask(mapRef.get(ScalableHashMap.class),
			       schedulerRef.get(ScheduleMapPutsTask.class),
			       count));
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
	    System.err.println("Total task run: " + totalRuns);
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
