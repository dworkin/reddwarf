package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Task;

import com.sun.sgs.app.util.DistributedHashMap;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs operations to kick-in native compilation and then enqueues a
 * {@link MapComparisonTask}.
 */
@SuppressWarnings({"unchecked"})	    
public class PrecompileMapOperationsAndThenTest implements Task, Serializable {

    private static final long serialVersionUID = 0;
    
    int count;
    
    public PrecompileMapOperationsAndThenTest() {
	this(0);
    }
    
    public PrecompileMapOperationsAndThenTest(int count) {
	this.count = count;
    }
    
    public void run() {
	try {
	    if (count + 1 < 500) {
		
		Map m = new HashMap<Integer,Integer>();
		Map m2 = new DistributedHashMap<Integer,Integer>();
		
		for (int j = 0; j < 1024; ++j) {
		    int i = (Math.random() >= .5) 
			? (int)(Integer.MAX_VALUE * Math.random())
			: (int)(Integer.MIN_VALUE * Math.random());
		    m.put(i,i);	   
		    m2.put(i,i);
		    m.get(i);
		    m2.get(i);
		}		    
		
		AppContext.getTaskManager().
		    scheduleTask(new PrecompileMapOperationsAndThenTest(count+1));
	    }
	    else {
		AppContext.getTaskManager().
		    scheduleTask(new MapComparisonTask());
	    }
	} catch (Throwable t) { 
	    t.printStackTrace();
	}
    }    
}
