/**
 * 
 * <p>
 * Title: PDTimer.java
 * </p>
 * <p>
 * Description:
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.gloutils.pdtimer;

import java.util.logging.Logger;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTimerListener;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * 
 * <p>
 * Title: PDTimer.java
 * </p>
 * <p>
 * Description: This is the primary GLO in the Persistant/Distributed
 * timer system
 * </p>
 * <p>
 * The iniate slice timer is non-persistant and local to the slice. The
 * PD timer system is a set of GLOs that are driven off the slice timer
 * manager but provide for the processing of any event on any slice, and
 * the persistance of registered events between runs of the slice.
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PDTimer implements SimTimerListener {

    /**
     * Hardwired Serial version UID
     */
    private static final long serialVersionUID = 1L;
    
    private static Logger log = Logger.getLogger("com.sun.gi.gloutils.pdtimer");

    GLOReference timerListRef = null;

    public PDTimer(SimTask task) {
        log.fine("init PDTimer");
        PDTimerEventList list = null;
        try {
            list = new PDTimerEventList();
        } catch (InstantiationException e) {

            e.printStackTrace();
        }
        timerListRef = task.createGLO(list, null);
    }

    public void start(SimTask task, long heartbeat)
            throws InstantiationException {

        try {
            task.registerTimerEvent(ACCESS_TYPE.PEEK, heartbeat * 1000, true,
                    task.makeReference(this));
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    public void timerEvent(long eventID) {
        // Note that when this is called we have just been PEEKed.
        // Thsi is intentional to prvent needless blocking by the
        // different
        // slices all servicing timer events
        log.finest("pd timer tick");
        SimTask task = SimTask.getCurrent();
        PDTimerEventList eventList = (PDTimerEventList) timerListRef.peek(task);
        eventList.tick(task, System.currentTimeMillis());
    }

    public GLOReference addTimerEvent(SimTask task, ACCESS_TYPE access,
            long delay, boolean repeat, GLOReference target, String methodName,
            Object[] parameters) {
        PDTimerEvent evnt = new PDTimerEvent(access, delay, repeat, target,
                methodName, parameters);
        GLOReference evntRef = task.createGLO(evnt, null);
        PDTimerEventList list = (PDTimerEventList) timerListRef.get(task);
        list.addEvent(task, evntRef);
        return evntRef;
    }

    public void removeTimerEvent(SimTask task, GLOReference eventRef) {
        PDTimerEventList list = (PDTimerEventList) timerListRef.get(task);
        list.removeEvent(eventRef);
    }

}
