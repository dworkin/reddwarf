package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import java.io.Serializable;
import java.util.Properties;

/**
 * A task that schedules a number of simple tasks, each of which runs a number
 * of times, optionally allocating a new task object for each run, and printing
 * performance information after each task has run a number of times.  This
 * test provides a simple metric for the performance of the task service.  By
 * default, the number of tasks scheduled equals the number of available
 * processors, but the value can be specified with the {@value #TASKS_KEY}
 * configuration property.  The total number of times the tasks are run can be
 * specified with the {@value #TOTAL_COUNT_KEY} property, and defaults to
 * {@value #DEFAULT_TOTAL_COUNT}.  Allocates a new object for each task run if
 * the {@value #ALLOCATE_KEY} configuration property is set to {@code true}.
 */
public class ScheduleSimpleTasks extends BasicScheduleTasks {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The configuration property for whether to create a new managed object
     * for each task run.
     */
    public static final String ALLOCATE_KEY =
	ScheduleSimpleTasks.class.getName() + ".allocate";

    /** Whether to allocate new tasks as managed objects. */
    private final boolean allocate;

    /**
     * Creates an instance using the specified configuration properties.
     *
     * @param properties the configuration properties
     */
    public ScheduleSimpleTasks(Properties properties) {
	super(properties);
	allocate = Boolean.valueOf(
	    properties.getProperty(ALLOCATE_KEY, "false"));
    }

    /** Creates a task to run. */
    protected Task createTask() {
	return (allocate)
	    ? new ManagedSimpleTask(this, count) : new SimpleTask(this, count);
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
	    if (--count <= 0) {
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
