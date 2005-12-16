/**
 *
 * <p>Title: TimeManager.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.framework.timer;

import java.lang.reflect.Method;

import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.Simulation.ACCESS_TYPE;

/**
 *
 * <p>Title: TimeManager.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface TimerManager {

	
	/**
	 * This method registers an event to be queued after the specified delay in MS.
	 * If repeating is set true it will be queued again in the same number of MS and
	 * continue doing that until removed.
	 * 
	 * Timer events are *not* persistant nor ditributed.  They need to be set up by the Boot 
	 * object of the app and always run local to the slice they are registered on.  
	 * We have provided a destributed and persistant timer GLO library runs of this local timer
	 * in app space to allow for destributed processing of persistant events.
	 * 
	 * @see com.sun.gi.gloutils.Timer
	 * 
	 * The system will make a best-effort to queue the task as close to the requested 
	 * time as possible.  Note that actual execution is then up to the code that handles
	 * executing queued tasks.
	 * 
	 * IMPORTANT:  The backend system will be configured with a minimum timer tick (default is
	 * 1 second.)  The size of the delay will  be rounded up to the nearest full multiple
	 * of that tick.  (eg, a 500 ms request at the default tick rate of 1sec (1000ms) will mean
	 * an actual event frequency of approximately 1/sec.)
	 * 
	 * @param sim The simulation who is requesting this timer event
	 * @param access 
	 * @param startObjectID ID of a GLO that implements TimerManagerListener to receive the event
	 * @param delay The time in ns to delay before queuint the event.
	 * @param repeat If false, this is a one shot, else it repeats
	 * @returns an ID for the event
	 */
	public long registerEvent(Simulation sim, ACCESS_TYPE access, long startObjectID, long delay, boolean repeat);
	
	/**
	 * Removes a task from the lost of timed events.
	 * @param eventID The ID returned from the call used to register the event.
	 */
	public void removeEvent(long eventID);
	
}
