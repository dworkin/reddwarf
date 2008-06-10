/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
    private final ManagedReference<ScheduleMapPutsTask> schedulerRef;

    /** A reference to the map. */
    private final ManagedReference<? extends Map<Integer, Integer>> mapRef;

    /** The remaining number of operations to run. */
    private int count;

    /**
     * Creates an instance.
     *
     * @param scheduler the object to notify when done
     * @param map the map
     * @param count the number of times to perform puts
     */
    public MapPutTask(
	ScheduleMapPutsTask scheduler, Map<Integer, Integer> map, int count)
    {
	DataManager dataManager = AppContext.getDataManager();
	schedulerRef = dataManager.createReference(scheduler);
	mapRef = dataManager.createReference(map);
	this.count = count;
    }

    /** Puts a random integer into the map and reschedules this task. */
    public void run() {
	if (--count <= 0) {
	    schedulerRef.get().taskDone();
	    return;
	}
	Map<Integer, Integer> map = mapRef.get();
	int i = random.nextInt();
	map.put(i, i);
	AppContext.getTaskManager().scheduleTask(this);
    }
}
