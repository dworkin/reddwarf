package com.sun.gi.logic;

import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.logic.impl.SimThreadImpl;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

/**
 * <p>
 * Title: Sim Kernel
 * </p>
 * <p>
 * Description: This is the interface to the logic engine. Each game
 * (simulation) in a slice has its own run-time sim object that implements this
 * interface and provides the operating context for the game.
 * </p>
 * <p>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p>
 * Company: Sun Microsystems, TMI
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface SimKernel {
	

	
	
	public void addSimulation(Simulation sim);
	
	public void simHasNewTask();

	public void removeSimulation(Simulation sim);

	/**
	 * @param impl
	 */
	public void returnToThreadPool(SimThreadImpl impl);

	/**
	 * @param timerManager
	 */
	public void setTimerManager(TimerManager timerManager);
	
	/**
	 * 
	 * @param access 
	 * @param sim
	 * @param ref
	 * @param delay
	 * @param repeat
	 * @return
	 */
	public long registerTimerEvent(ACCESS_TYPE access, Simulation sim, GLOReference ref, long delay,
			boolean repeat);
}
