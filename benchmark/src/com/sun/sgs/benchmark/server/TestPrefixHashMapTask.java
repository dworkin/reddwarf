package com.sun.sgs.benchmark.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionException;

import com.sun.sgs.app.util.PrefixHashMap;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A single master task
 *
 */
public class TestPrefixHashMapTask implements AppListener, Serializable {

    private static final String APP_KEY = "com.sun.sgs.benchmark.app";

    private static final long serialVersionUID = 0x7FADEFF;

    public TestPrefixHashMapTask() {

    }

    public void initialize(Properties properties) {

 	TaskManager tm = AppContext.getTaskManager();
 	DataManager dm = AppContext.getDataManager();


	PrefixHashMap<Integer,Integer> test = 
	    new PrefixHashMap<Integer, Integer>(16);
	HashMap<Integer, Integer> control = 
	    new HashMap<Integer, Integer>();

//   	tm.schedulePeriodicTask(new PrefixHashMapPut(dm.createReference(test)),
//   				500, 100);
//   	tm.schedulePeriodicTask(new PrefixHashMapPut(dm.createReference(test)),
//   				900, 100);
//   	tm.schedulePeriodicTask(new PrefixHashMapPut(dm.createReference(test)),
//   				800, 100);

// 	tm.schedulePeriodicTask(new PrefixHashMapRemove(dm.createReference(test)),
// 				1000, 200);

// 	tm.schedulePeriodicTask(new PrefixHashMapGet(dm.createReference(test)),
// 				600, 225);
// 	tm.schedulePeriodicTask(new PrefixHashMapGet(dm.createReference(test)),
// 				1090, 50);

// 	tm.schedulePeriodicTask(new PrefixHashMapPut(dm.createReference(test)),
// 				2000, 300);
// 	tm.schedulePeriodicTask(new PrefixHashMapPut(dm.createReference(test)),
// 				3000, 300);

	/*
	 * Operations that will cause infinite timeouts when run too long
	 */

//  	tm.schedulePeriodicTask(new PrefixHashMapSize(dm.createReference(test)),
//  				1000, 10000);

//  	tm.schedulePeriodicTask(
//  	     new HashMapPut(dm.createReference(new MapWrapper(control))),
//  	     1000, 100);

	
	/*
	 * Tests for serialiaztion overhead
	 */

	int size = 16; // how large the total object is in kilobytes
	int scale = 1; // must be factor of 2
	

	ManagedArray arr = new ManagedArray(1024 * size);
	ManagedReference[] refs = new ManagedReference[size * scale];
	for (int i = 0; i < refs.length; ++i)
	    refs[i] = dm.createReference(new ManagedArray(1024 / scale));
	
  	tm.schedulePeriodicTask(new DatastoreAccessChunk(dm.createReference(arr)),
  				500, 100);

  	tm.schedulePeriodicTask(new DatastoreAccessSegments(refs), 1000, 100);



	/*
	 * Basic timing tasks for establishing a base cost
	 */
//   	tm.schedulePeriodicTask(new NoOpTimingTask(), 0, 100);

//   	tm.schedulePeriodicTask(new BaseCaseGetForUpdateTimingTask(dm.createReference(new Empty())), 0, 100);
//   	tm.schedulePeriodicTask(new BaseCaseGetAndMarkForUpdateTimingTask(dm.createReference(new Empty())), 30, 100);

//   	tm.schedulePeriodicTask(new BaseCaseGetTimingTask(dm.createReference(new Empty())), 60, 100);
    }

    public ClientSessionListener loggedIn(ClientSession session) {
	return null;
    }


    private static class NoOpTimingTask implements Task, Serializable {

	public NoOpTimingTask() { }

	public void run() {

	}
    }

    private static class BaseCaseGetTimingTask implements Task, Serializable {

	ManagedReference ref;
	
	public BaseCaseGetTimingTask(ManagedReference ref) { this.ref = ref; }

	public void run() {
	    ref.get(Object.class);
	}

    }

    private static class BaseCaseGetForUpdateTimingTask
	implements Task, Serializable {

	ManagedReference ref;
	
	public BaseCaseGetForUpdateTimingTask(ManagedReference ref) { this.ref = ref; }

	public void run() {
	    ref.getForUpdate(Object.class);
	}

    }

    private static class BaseCaseGetAndMarkForUpdateTimingTask
	implements Task, Serializable {

	ManagedReference ref;
	
	public BaseCaseGetAndMarkForUpdateTimingTask(ManagedReference ref) { this.ref = ref; }

	public void run() {
	    Empty e = ref.get(Empty.class);
	    AppContext.getDataManager().markForUpdate(e);
	}

    }


    private static class Empty implements ManagedObject, Serializable {

	private static final long serialVersionUID = 0;

	public Empty() {

	}
    } 


    @SuppressWarnings({"unchecked"})
    private static class PrefixHashMapPut implements Task, Serializable {

	private final ManagedReference map;

	static int num = 0;

	int id;

 	int size;

	public PrefixHashMapPut(ManagedReference map) {
	    this.map = map;
 	    size = 0;	    
	    id = num++;
	}

	public void run() {
	    try {
		Map m = map.get(Map.class);	    
		int i = (int)(Integer.MAX_VALUE * Math.random());
		m.put(i,i);	   
// 		long size = (AppContext.getDataManager().
// 			    getBinding("MAP_SIZE", ManagedLong.class)).incrementAndGet();		
 		if (++size % 100 == 0)
 		    System.out.printf("%s put %d ints into the PrefixHashMap\n", this, size);
	    } catch (Throwable te ) { }
	}
	
	public String toString() {
	    return "PrefixHashMapPut[" + id + "]";
	}
    }

    @SuppressWarnings({"unchecked"})
    private static class PrefixHashMapRemove implements Task, Serializable {

	private final ManagedReference map;

	int c;

	public PrefixHashMapRemove(ManagedReference map) {
	    this.map = map;
	    c = 0;	    
	}

	public void run() {
	    try {
		Map m = map.get(Map.class);	    
		int i = (int)(Integer.MAX_VALUE * Math.random());
		Object o = m.remove(i);	   
		if (o != null && ++c % 100 == 0)
		    System.out.printf("removing object %d into the PrefixHashMap\n", c);
	    } catch (Throwable te ) { }
	}
    }


    @SuppressWarnings({"unchecked"})
    private static class PrefixHashMapGet implements Task, Serializable {

	private final ManagedReference map;

	int c;

	public PrefixHashMapGet(ManagedReference map) {
	    this.map = map;
	    c = 0;	    
	}

	public void run() {
	    try {
		Map m = map.get(Map.class);	    
		int i = (int)(Integer.MAX_VALUE * Math.random());
		Object o = m.get(i);	   
		if (o != null && ++c % 100 == 0)
		    System.out.printf("got object %d into the PrefixHashMap\n", c);
	    } catch (Throwable te ) { }
	}
    }

    @SuppressWarnings({"unchecked"})
    private static class PrefixHashMapSize implements Task, Serializable {

	private final ManagedReference map;

	public PrefixHashMapSize(ManagedReference map) {
	    this.map = map;
	}

	public void run() {
	    try {
		Map m = map.get(Map.class);	    
		System.out.printf("prefixHashMap.size() = %d\n", m.size());
	    } catch (Throwable te ) { }
	}
    }


    
    @SuppressWarnings({"unchecked"})
    private static class HashMapPut implements Task, Serializable {

	private final ManagedReference mapWrapper;

	int c;

	public HashMapPut(ManagedReference mapWrapper) {
	    this.mapWrapper = mapWrapper;
	    c = 0;
	}

	public void run() {
	    Map m = mapWrapper.get(MapWrapper.class).getMap();
	    int i = (int)(Integer.MAX_VALUE * Math.random());
	    if (++c % 100 == 0)
	    System.out.printf("putting object %d into the HashMap\n", c);
	    m.put(i,i);	   
	}
    }

    @SuppressWarnings({"unchecked"})
    private static class DatastoreAccessChunk implements Task, Serializable {

	private final ManagedReference arrRef;

	public DatastoreAccessChunk(ManagedReference arrRef) {
	    this.arrRef = arrRef;
	}

	public void run() {
	    ManagedArray m = arrRef.get(ManagedArray.class);	    
	}
    }

    @SuppressWarnings({"unchecked"})
    private static class DatastoreAccessSegments implements Task, Serializable {

	private final ManagedReference[] refs;

	public DatastoreAccessSegments(ManagedReference[] refs) {
	    this.refs = refs;
	}

	public void run() {
	    for (ManagedReference r : refs) {
		ManagedArray m = r.get(ManagedArray.class);
	    }
	}
    }


    private static class ManagedLong implements Serializable, ManagedObject {

	private long val;

	public ManagedLong() {
	    val = 0;
	}

	public long incrementAndGet() {
	    AppContext.getDataManager().markForUpdate(this);
	    return ++val;
	}

    }

    private static class ManagedArray implements Serializable, ManagedObject {

	private final byte[] arr;

	public ManagedArray(int bytes) {
	    arr = new byte[bytes - 16];
	}

    }

    @SuppressWarnings({"unchecked"})
    private static class MapWrapper implements Serializable, ManagedObject {

	private final Map m;

	public MapWrapper(Map m) {
	    this.m = m;
	}
	
	public Map getMap() {
	    // we know we are going to update the name
	    AppContext.getDataManager().markForUpdate(this);
	    return m;
	}

    }
    
}