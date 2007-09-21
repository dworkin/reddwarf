package com.sun.sgs.analysis.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.app.util.DistributedHashMap;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@SuppressWarnings({"unchecked"})
public class DistributedHashMapPut implements Task, Serializable {
	    
    private final ManagedReference map;

    public DistributedHashMapPut(ManagedReference map) {
	this.map = map;
    }
    
    public void run() {
	try {
	    Map m = map.get(Map.class);	    
	    int i = (Math.random() >= .5) 
		? (int)(Integer.MAX_VALUE * Math.random())
		: (int)(Integer.MIN_VALUE * Math.random());
		m.put(i,i);	   
	} catch (Throwable te ) { }
    }
}
