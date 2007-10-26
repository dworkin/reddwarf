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
    private final ManagedReference map;

    /**
     * Creates an instance with the specified reference, which should refer to
     * the map.
     */
    public MapPutTask(ManagedReference map) {
	if (map == null) {
	    throw new NullPointerException("The map must not be null");
	}
	this.map = map;
    }

    /** Puts a random integer into the map and reschedules this task. */
    public void run() {
	@SuppressWarnings("unchecked")
	Map<Object, Object> m = map.get(Map.class);
	int i = random.nextInt();
	m.put(i, i);
	AppContext.getTaskManager().scheduleTask(this);
    }
}
