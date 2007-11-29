package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import java.io.Serializable;
import java.util.Map;
import java.util.Random;

/** A task that puts a random integer into a map and reschedules itself. */
public class MapPutTask implements ManagedObject, Serializable, Task {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** Generates the random integer. */
    private static final Random random = new Random();

    /** A reference to the object to notify when done. */
    private final ManagedReference schedulerRef;

    /** A reference to the map. */
    private final ManagedReference mapRef;

    /** The remaining number of operations to run. */
    private int count;

    /** Creates an instance with the specified map. */
    public MapPutTask(ScheduleMapPutsTask scheduler, Map map, int count) {
	DataManager dataManager = AppContext.getDataManager();
	schedulerRef = dataManager.createReference(scheduler);
	mapRef = dataManager.createReference((ManagedObject) map);
	this.count = count;
    }

    /** Puts a random integer into the map and reschedules this task. */
    public void run() {
	if (--count <= 0) {
	    schedulerRef.get(ScheduleMapPutsTask.class).taskDone();
	    return;
	}
	@SuppressWarnings("unchecked")
	Map<Object, Object> map = mapRef.get(Map.class);
	int i = random.nextInt();
	map.put(i, i);
	AppContext.getTaskManager().scheduleTask(this);
    }
}
