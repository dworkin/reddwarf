package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.util.ScalableHashMap;
import java.io.Serializable;
import java.util.HashMap;

/**
 *
 */
public class MapComparisonTask implements Task, Serializable {

    private static final long serialVersionUID = 1;
    
    public MapComparisonTask() { }
    
    public void run() {
	System.out.println("starting tests");
	TaskManager tm = AppContext.getTaskManager();
	DataManager dm = AppContext.getDataManager();
	ScalableHashMap<Integer, Integer> map = 
	    new ScalableHashMap<Integer, Integer>();
	ManagedReference ref = dm.createReference(map);
	for (int i = 0; i < 8; i++) {
	    tm.schedulePeriodicTask(new DistributedHashMapPut(ref),
				    500 + (i * 10), 20);
	}
    }
} 

