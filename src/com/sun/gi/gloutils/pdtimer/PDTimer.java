/**
 *
 * <p>Title: PDTimer.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.gloutils.pdtimer;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTimerListener;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 *
 * <p>Title: PDTimer.java</p>
 * <p>Description: This is the primary GLO in the Persistant/Distributed timer system</p>
 * <p>The iniate slice timer is non-persistant and local to the slice.  The PD timer
 * system is a set of GLOs that are driven off the slice timer manager but provide
 * for the processing of any event on any slice, and the persistance of registered
 * events between runs of the slice.
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PDTimer implements SimTimerListener {
	/**
	 * Hardwired Serial version UID
	 */
	private static final long serialVersionUID = 1L;
	GLOReference timerListRef=null;
	
	public PDTimer(SimTask task){	
		System.out.println("initting PDTimer");
		PDTimerEventList list=null;
		try {
			list = new PDTimerEventList();
		} catch (InstantiationException e) {
			
			e.printStackTrace();
		}			
		timerListRef = task.createGLO(list,null);
	}
	
	public void start(SimTask task,long heartbeat) throws InstantiationException{
		
		try {
			task.registerTimerEvent(ACCESS_TYPE.PEEK,heartbeat*1000,true,task.makeReference(this));
		} catch (InstantiationException e) {			
			e.printStackTrace();
		}		
	}
	
	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimTimerListener#timerEvent(com.sun.gi.logic.SimTask, long)
	 */
	public void timerEvent(SimTask task, long eventID) {
		// NOte that when this is called we have just been PEEKed.
		// Thsi is intentional to prvent needless blocking by the different
		// slices all servicing timer events
		System.out.println("pd timer tick");
		PDTimerEventList eventList = (PDTimerEventList)timerListRef.peek(task);
		eventList.tick(task,System.currentTimeMillis());		
	}
	
	public GLOReference addTimerEvent(SimTask task, ACCESS_TYPE access, long delay,boolean repeat,
			GLOReference target, String methodName, Object[] parameters){
		PDTimerEvent evnt = new PDTimerEvent(access,delay,repeat,target,methodName,parameters);
		GLOReference evntRef = task.createGLO(evnt,null);
		PDTimerEventList list = (PDTimerEventList)timerListRef.get(task);
		list.addEvent(task,evntRef);
		return evntRef;
	} 
	
	public void removeTimerEvent(SimTask task,GLOReference eventRef){
		PDTimerEventList list = (PDTimerEventList)timerListRef.get(task);
		list.removeEvent(eventRef);
	}

	

}
