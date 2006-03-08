package com.sun.gi.gloutils.pdtimer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.logging.Logger;

import com.sun.gi.logic.GLO;
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
public class PDTimerEventList implements GLO {
        private static Logger log = Logger.getLogger("com.sun.gi.gloutils.pdtimer");
	private SortedMap<Long, HashSet<GLOReference>> timerEvents =
        new TreeMap<Long, HashSet<GLOReference>>();
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
		log.finest("Ticking timer list");
		List<GLOReference> cleanupList = new ArrayList<GLOReference>();
		for (Entry<Long, HashSet<GLOReference>> entry : timerEvents.entrySet()) {
			if (entry.getKey() <= time) {
                for (GLOReference eventRef : entry.getValue()) {
                    PDTimerEvent event = (PDTimerEvent) eventRef.attempt(task);
                    if (event != null) { // if null then we can skip
                        if (event.isActive()) { // if active then do it
                            event.fire(task);
                            if (event.requiresCleanup()) {
                                cleanupList.add(eventRef);
                            }
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
							 List.class);				
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
	 *
	 * @param task
	 * @param evntRef
	 */
	public void addEvent(SimTask task, GLOReference evntRef) {
		task.access_check(ACCESS_TYPE.GET,this);
		PDTimerEvent evnt = (PDTimerEvent) evntRef.peek(task);
        long fireTime = evnt.delayTime() + System.currentTimeMillis();
        HashSet bucket = timerEvents.get(fireTime);

        if (bucket == null) {
            bucket = new HashSet();
            timerEvents.put(new Long(fireTime), bucket);
        }

        bucket.add(evntRef);
	}

	/**
	 * (DESIGNED To BE CALLED WITH ACCESS.GET)
	 * @param eventRef
	 */
	public void removeEvent(GLOReference eventRef) {
        for (Iterator it = timerEvents.entrySet().iterator(); it.hasNext();) {
            Entry<Long,HashSet<GLOReference>> entry =
                (Entry<Long,HashSet<GLOReference>>) it.next();
            if (entry.getValue().contains(eventRef)) {
                if (entry.getValue().size() == 1)
                    it.remove();
                else
                    entry.getValue().remove(eventRef);
                return;
            }
        }
	}

	// called from a task
	//  * (DESIGNED To BE CALLED WITH ACCESS.GET)
	public void cleanup(List<GLOReference> cleanupList) {
		SimTask task = SimTask.getCurrent();
		task.access_check(ACCESS_TYPE.GET,this);
		log.finest("Doing cleanup");
		/*if (--bigCleanupCountdown==0){ // do a big cleanup
			bigCleanup(task);
            } else*/ { // do a normal destributed cleanup
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
		for(Entry<Long,HashSet<GLOReference>> entry : timerEvents.entrySet()){
			if(entry.getKey().longValue()<=time){
                for (GLOReference ref : entry.getValue()) {
                    PDTimerEvent evnt = (PDTimerEvent)ref.get(task);
                    if (evnt.requiresCleanup()){ //needs to be cleaned					
                        if (evnt.isRepeating()) {					
                            System.out.println("re-installing");
                            removeEvent(ref);
                            evnt.reset(task); // resets it for next ring
                            addEvent(task, ref);
                        } else if (evnt.isMoribund()) {
                            removeEvent(ref);
                            ref.delete(task);
                        }
                    }
                }
			} else {
				break; // end of events that have fired already
			}
		}
	}

}
