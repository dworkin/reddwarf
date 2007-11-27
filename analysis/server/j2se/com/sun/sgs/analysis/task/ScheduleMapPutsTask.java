package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
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
 * but the value can be specified with the {@value #NUM_TASKS_KEY} system
 * property.
 */
public class ScheduleMapPutsTask implements Task, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The name of the configuration property that specifies the number of
     * tasks to run.
     */
    public static final String NUM_TASKS_KEY =
	ScheduleMapPutsTask.class.getName() + ".num.tasks";

    /** The number of tasks to run. */
    private final int numTasks;

    /**
     * Creates an instance of this class using the specified configuration
     * properties.
     */
    public ScheduleMapPutsTask(Properties properties) { 
	String numTasksString = properties.getProperty(NUM_TASKS_KEY);
	if (numTasksString == null) {
	    numTasks = Runtime.getRuntime().availableProcessors();
	} else {
	    numTasks = Integer.parseInt(numTasksString);
	}
    }

    /** Schedules the tasks. */
    public void run() {
	System.out.println("starting " + numTasks + " tasks");
	TaskManager tm = AppContext.getTaskManager();
	ScalableHashMap<Integer, Integer> map =
	    new ScalableHashMap<Integer, Integer>();
	for (int i = 0; i < numTasks; i++) {
	    tm.scheduleTask(new MapPutTask(map));
	}
    }
}
