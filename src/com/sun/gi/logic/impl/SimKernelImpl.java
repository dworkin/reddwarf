package com.sun.gi.logic.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.SimThread;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.Simulation.ACCESS_TYPE;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.multicast.util.UnimplementedOperationException;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */

public class SimKernelImpl implements SimKernel {
	private ObjectStore ostore;
	private TimerManager timerManager;

	private List<Simulation> simList = new ArrayList<Simulation>();

	private List<SimThreadImpl> threadPool = new LinkedList<SimThreadImpl>();

public SimKernelImpl(ObjectStore ostore) {
		this.ostore = ostore;
		int startingPoolSize = 3;
		String poolSzStr = System.getProperty("sgs.kernel.thread_pool_sz");
		if (poolSzStr!=null ){
			startingPoolSize  = Integer.parseInt(poolSzStr);
		}
		for(int i=0;i<startingPoolSize;i++){
			new SimThreadImpl(this,ostore);
		}
		// round robin assign threads to tasks from our sim list
		// this could be a palce where we add prioritization later
		// ALternately each sim could be wrappedin an isolate that handled its own threads
		// for MVM
		
		new Thread(new Runnable(){
			public void run() {
				while(true){
					
					synchronized(simList){				
						boolean tasksAvailable = false;
						while (!tasksAvailable){
							for(Simulation sim : simList){
								if (sim.hasTasks()){
									tasksAvailable = true;
									break;
								}
							}
							if (!tasksAvailable){
								try {
									simList.wait();
								} catch (InterruptedException e) {									
									e.printStackTrace();
								}
							}
						}	
						for(Simulation sim : simList){
							synchronized(threadPool){
								if (threadPool.size()>0){
									if (sim.hasTasks()){
										SimTask task = sim.nextTask();
										SimThread thread = threadPool.remove(0);
										thread.execute(task);
									}
								} else {
									try {
										threadPool.wait();
									} catch (InterruptedException e) {										
										e.printStackTrace();
									}
								}
							}
						}
					}					
				}
			}
		}).start();	
	}

	public void addSimulation(Simulation sim) {
		synchronized (simList) {
			simList.add(sim);
			simList.notify();
		}
	}
	
	public void simHasNewTask(){
		synchronized(simList){
			simList.notify();
		}
	}

	public void removeSimulation(Simulation sim) {
		synchronized (simList) {
			simList.remove(sim);
		}
	}

	public Transaction newTransaction(long appID, ClassLoader loader) {
		return ostore.newTransaction(appID, loader);
	}

	public ObjectStore getOstore() {
		return ostore;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimKernel#returnToThreadPool(com.sun.gi.logic.impl.SimThreadImpl)
	 */
	public void returnToThreadPool(SimThreadImpl impl) {
		synchronized(threadPool){
			threadPool.add(impl);
		}
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimKernel#setTimerManager(com.sun.gi.framework.timer.TimerManager)
	 */
	public void setTimerManager(TimerManager timerManager) {
		this.timerManager = timerManager;
		
	}
	
	public long registerTimerEvent(ACCESS_TYPE access, Simulation sim, GLOReference ref, long delay,
			boolean repeat){
		return timerManager.registerEvent(sim,access, ((GLOReferenceImpl)ref).objID,delay,repeat);
	}

}
