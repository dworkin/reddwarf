package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
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

    /** A reference to the map. */
    private final ManagedReference mapRef;

    /** Creates an instance with the specified map. */
    public MapPutTask(Map map) {
	if (map == null) {
	    throw new NullPointerException("The map must not be null");
	}
	mapRef = AppContext.getDataManager().createReference(
	    (ManagedObject) map);
    }

    /** Puts a random integer into the map and reschedules this task. */
    public void run() {
	@SuppressWarnings("unchecked")
	Map<Object, Object> map = mapRef.get(Map.class);
	int i = random.nextInt();
	map.put(i, i);
	AppContext.getTaskManager().scheduleTask(this);
    }
}
