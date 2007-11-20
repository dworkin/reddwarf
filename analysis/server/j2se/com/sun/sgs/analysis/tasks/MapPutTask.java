package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import java.io.Serializable;
import java.util.Map;
import java.util.Random;

/**
 * A task that puts a random integer into a map and reschedules itself a
 * specified number of times. */
public class MapPutTask implements ManagedObject, Serializable, Task {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** Generates the random integer. */
    private static final Random random = new Random();

    /** A reference to the map. */
    private final ManagedReference mapRef;

    /** A reference to the object to notify when done. */
    private final ManagedReference schedulerRef;

    /** The remaining number of times to run. */
    private int remaining;

    /** Creates an instance with the specified map. */
    public MapPutTask(Map map, ScheduleMapPutsTask scheduler, int count) {
	if (map == null) {
	    throw new NullPointerException("The map must not be null");
	}
	DataManager dataManager = AppContext.getDataManager();
	mapRef = dataManager.createReference((ManagedObject) map);
	schedulerRef = dataManager.createReference(scheduler);
	remaining = count;
    }

    /** Puts a random integer into the map and reschedules this task. */
    public void run() {
	@SuppressWarnings("unchecked")
	Map<Object, Object> map = mapRef.get(Map.class);
	int i = random.nextInt();
	map.put(i, i);
	remaining--;
	if (remaining == 0) {
	    schedulerRef.get(ScheduleMapPutsTask.class).taskDone();
	} else {
	    AppContext.getTaskManager().scheduleTask(this);
	}
    }
}
