package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.util.ScalableHashMap;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Runs operations to kick-in native compilation and then enqueues a
 * {@link MapComparisonTask}.
 */
public class PrecompileMapOperationsAndThenTest implements Task, Serializable {
    private static final Random random = new Random();

    private static final long serialVersionUID = 0;
    
    private final int count;
    
    public PrecompileMapOperationsAndThenTest() {
	this(0);
    }
    
    public PrecompileMapOperationsAndThenTest(int count) {
	this.count = count;
    }
    
    public void run() {
	try {
	    if (count + 1 < 500) {
		
		Map<Integer, Integer> m = new HashMap<Integer, Integer>();
		Map<Integer, Integer> m2 =
		    new ScalableHashMap<Integer, Integer>();
		
		for (int j = 0; j < 1024; ++j) {
		    int i = random.nextInt();
		    m.put(i,i);	   
		    m2.put(i,i);
		    m.get(i);
		    m2.get(i);
		}		    
		
		AppContext.getTaskManager().
		    scheduleTask(
			new PrecompileMapOperationsAndThenTest(count+1));
	    } else {
		AppContext.getTaskManager().
		    scheduleTask(new MapComparisonTask());
	    }
	} catch (Exception e) { 
	    e.printStackTrace();
	}
    }    
}
