package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.util.ScalableHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A task that creates a {@link ScalableHashMap}, and schedules a number of
 * {@link MapPutTask}s to insert random integers into it.  By default, the
 * number of tasks scheduled equals the number of available processors, as
 * returned by {@link Runtime#availableProcessors Runtime.availableProcessors},
 * but the value can be specified with the {@value
 * com.sun.sgs.analysis.task.BasicScheduleTasks#TASKS_KEY} system property.
 */
public class ScheduleMapPutsTask extends BasicScheduleTasks {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** A reference to the map or null. */
    private ManagedReference mapRef;

    /**
     * Creates an instance of this class using the specified configuration
     * properties.
     */
    public ScheduleMapPutsTask(Properties properties) { 
	super(properties);
    }

    /** Schedules the tasks. */
    public void run() {
	mapRef = AppContext.getDataManager().createReference(
	    new ScalableHashMap<Integer, Integer>());
	super.run();
    }

    /** Creates a task to run. */
    protected Task createTask() {
	@SuppressWarnings("unchecked")
	Map<Object, Object> map = mapRef.get(Map.class);
	return new MapPutTask(this, map, count);
    }
}
