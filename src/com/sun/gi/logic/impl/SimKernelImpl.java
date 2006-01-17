package com.sun.gi.logic.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.sun.gi.framework.rawsocket.RawSocketManager;
import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimThread;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

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

	private TimerManager timerManager;
	private RawSocketManager socketManager;

	private List<Simulation> simList = new ArrayList<Simulation>();

	private List<SimThreadImpl> threadPool = new LinkedList<SimThreadImpl>();

public SimKernelImpl() {
	
		int startingPoolSize = 3;
		String poolSzStr = System.getProperty("sgs.kernel.thread_pool_sz");
		if (poolSzStr!=null ){
			startingPoolSize  = Integer.parseInt(poolSzStr);
		}
		for(int i=0;i<startingPoolSize;i++){
			new SimThreadImpl(this);
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

	

	

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimKernel#returnToThreadPool(com.sun.gi.logic.impl.SimThreadImpl)
	 */
	public void returnToThreadPool(SimThreadImpl impl) {
		synchronized(threadPool){
			threadPool.add(impl);
			threadPool.notify();		// Sten added 1/13/06 -- prevents deadlocks if the pool is waiting.
		}
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimKernel#setTimerManager(com.sun.gi.framework.timer.TimerManager)
	 */
	public void setTimerManager(TimerManager timerManager) {
		this.timerManager = timerManager;
		
	}
	
	public long registerTimerEvent(long tid, ACCESS_TYPE access, Simulation sim, long objID, long delay,
			boolean repeat){
		return timerManager.registerEvent(tid,sim,access, objID,delay,repeat);
	}
	
//	 Hooks into the RawSocketManager, added 1/16/2006
	
	/**
	 * Sets the Raw Socket Manager.
	 * 
	 */
	public void setRawSocketManager(RawSocketManager socketManager) {
		this.socketManager = socketManager;
	}
	
	
	/**
	 * Requests that a socket be opened at the given host on the given port.
	 * The returned ID can be used for future communication with the socket that will
	 * be opened.  The socket ID will not be valid, and therefore should not be used 
	 * until the connection is complete.  Connection is complete once the 
	 * SimRawSocketListener.socketOpened() call back is called.
	 * 
	 * @param sim				the simulation requesting the connection.
	 * @param access			the access type (GET, PEEK, or ATTEMPT)
	 * @param ref				a reference to the GLO initiating the connection.
	 * @param host				a String representation of the remote host.
	 * @param port				the remote port.
	 * @param reliable			if true, the connection will use a reliable protocol.
	 * 
	 * @return an identifier that can be used for future communication with the socket.
	 */
	public long openSocket(long sid, Simulation sim, ACCESS_TYPE access, long objID, 
			String host, int port, boolean reliable) {
		
		return socketManager.openSocket(sid,sim, access, objID, host, port, reliable);
	}	
	
	/**
	 * Sends data on the socket mapped to the given socketID.  This method 
	 * will not return until the entire buffer has been drained.
	 * 
	 * @param socketID			the socket identifier.
	 * @param data				the data to send.  The buffer should be in a ready
	 * 							state, i.e. flipped if necessary. 
	 * 
	 * @return the number of bytes sent.
	 */
	public long sendRawSocketData(long socketID, ByteBuffer data) {
		return socketManager.sendData(socketID, data);
	}
	
	/**
	 * Requests that the socket matching the given socketID be closed.
	 * The socket should not be assumed to be closed, however, until the 
	 * call back SimRawSocketListener.socketClosed() is called.
	 * 
	 * @param socketID		the identifier of the socket.
	 */
	public void closeSocket(long socketID) {
		socketManager.closeSocket(socketID);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimKernel#getNextTimerID()
	 */
	public long getNextTimerID() {
		// TODO Auto-generated method stub
		return timerManager.getNextTimerID();
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimKernel#getNextSocketID()
	 */
	public long getNextSocketID() {		
		return socketManager.getNextSocketID();
	}

}
