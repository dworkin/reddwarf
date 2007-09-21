package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.app.util.DistributedHashMap;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;


/**
 *
 */
public class MapComparisonTask implements Task, Serializable {
    
    private static final long serialVersionUID = 0;        
    
    public MapComparisonTask() {
	
    }
    
    public void run() {
	
	System.out.println("starting tests");
	
	TaskManager tm = AppContext.getTaskManager();
	DataManager dm = AppContext.getDataManager();
	
	DistributedHashMap<Integer,Integer> regular = 
	    new DistributedHashMap<Integer, Integer>();
	    
	for (int i = 0; i < 8; ++i) {
	    tm.schedulePeriodicTask(
				    new DistributedHashMapPut(dm.createReference(regular)),
				    500 + (i * 10), 20);
	}


//   	    HashMap<Integer,Integer> control = new HashMap<Integer, Integer>();
	    
//  	    for (int i = 0; i < 1; ++i)
//  		tm.schedulePeriodicTask(
//  		    new HashMapPut(dm.createReference(new MapWrapper(control))),
//  		    1000, 50);

    }
} 

