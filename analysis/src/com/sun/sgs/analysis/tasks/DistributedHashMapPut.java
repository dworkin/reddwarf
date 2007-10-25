package com.sun.sgs.analysis.task;

import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import java.io.Serializable;
import java.util.Map;
import java.util.Random;

/**
 *
 */
public class DistributedHashMapPut implements Task, Serializable {
    private static final long serialVersionUID = 1;

    private static final Random random = new Random();

    private final ManagedReference map;

    public DistributedHashMapPut(ManagedReference map) {
	this.map = map;
    }

    public void run() {
	@SuppressWarnings("unchecked")
	Map<Object, Object> m = map.get(Map.class);
	int i = random.nextInt();
	m.put(i, i);
    }
}
