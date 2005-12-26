/**
 *
 * <p>Title: PDTimerEventList.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.gloutils.pdtimer;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * 
 * <p>
 * Title: PDTimerEventList.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PDTimerEventList implements Serializable {
	private SortedMap<Long, GLOReference> timerEvents = new TreeMap<Long, GLOReference>();
	private static final int BIGCLEANUP_PERIOD = 100;
	private int bigCleanupCountdown = BIGCLEANUP_PERIOD;
	

	/**
	 * Hardwired Serial Version UID
	 */
	private static final long serialVersionUID = 1L;
	
	public PDTimerEventList() throws InstantiationException{
		
		
	}

	//Tick is desgined to be called with ACCESS.PEEK
	public void tick(SimTask task, long time) {
		task.access_check(ACCESS_TYPE.PEEK,this);
		System.out.println("Ticking timer list");
		List<GLOReference> cleanupList = new ArrayList<GLOReference>();
		for (Entry<Long, GLOReference> entry : timerEvents.entrySet()) {
			if (entry.getKey() <= time) {
				GLOReference eventRef = entry.getValue();
				PDTimerEvent event = (PDTimerEvent) eventRef.attempt(task);
				if (event != null) { // if null then we can skip
					if (event.isActive()) { // if active then do it
						event.fire(task);
						if (event.requiresCleanup()) {
							cleanupList.add(eventRef);
						}
					}
				}
			} else {
				break; // out of events
			}
		}
		if (cleanupList.size() > 0) {
			try {				

				Method cleanupMethod = PDTimerEventList.class.getMethod("cleanup",
							SimTask.class, List.class);				
				task.queueTask(ACCESS_TYPE.GET, task.makeReference(this),
						cleanupMethod, new Object[] { cleanupList });
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (SecurityException e) {				
				e.printStackTrace();
			} catch (NoSuchMethodException e) {				
				e.printStackTrace();
			}
		}		
		
	}

	/**
	 * (DESIGNED To BE CALLED WITH ACCESS.GET)
	 * @param evntRef
	 */
	public void addEvent(SimTask task, GLOReference evntRef) {
		task.access_check(ACCESS_TYPE.GET,this);
		PDTimerEvent evnt = (PDTimerEvent) evntRef.peek(task);
		timerEvents.put(
				new Long(evnt.delayTime() + System.currentTimeMillis()),
				evntRef);

	}

	/**
	 * (DESIGNED To BE CALLED WITH ACCESS.GET)
	 * @param eventRef
	 */
	public void removeEvent(GLOReference eventRef) {
		for (Iterator i = timerEvents.entrySet().iterator(); i.hasNext();) {
			Entry entry = (Entry) i.next();
			if (eventRef.equals(entry.getValue())) {
				i.remove();
				return;
			}
		}
	}

	// called from a task
	//  * (DESIGNED To BE CALLED WITH ACCESS.GET)
	public void cleanup(SimTask task, List<GLOReference> cleanupList) {
		task.access_check(ACCESS_TYPE.GET,this);
		System.out.println("DOing cleanup");
		if (--bigCleanupCountdown==0){ // do a big cleanup
			bigCleanup(task);
		} else { // do a normal destributed cleanup
			for (GLOReference ref : cleanupList) {
				PDTimerEvent evnt = (PDTimerEvent) ref.get(task);
				if (evnt.isRepeating()) {					
					removeEvent(ref);
					evnt.reset(task); // resets it for next ring
					addEvent(task, ref);
				} else if (evnt.isMoribund()) {
					removeEvent(ref);
					ref.delete(task);
				}
			}
		}
	}
	
	// to handle lost cleanups, scrubs the whole list
	// * (DESIGNED To BE CALLED WITH ACCESS.GET)
	private void bigCleanup(SimTask task){
		long time = System.currentTimeMillis();
		for(Entry<Long,GLOReference> entry : timerEvents.entrySet()){
			if(entry.getKey().longValue()<=time){
				GLOReference ref = entry.getValue();
				PDTimerEvent evnt = (PDTimerEvent)ref.get(task);
				if (evnt.requiresCleanup()){ //needs to be cleaned					
					if (evnt.isRepeating()) {					
						removeEvent(ref);
						evnt.reset(task); // resets it for next ring
						addEvent(task, ref);
					} else if (evnt.isMoribund()) {
						removeEvent(ref);
						ref.delete(task);
					}
				}
			} else {
				break; // end of events that have fired already
			}
		}
	}

}
