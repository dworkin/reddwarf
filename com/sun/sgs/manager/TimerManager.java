
/*
 * TimerManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Jul 10, 2006	12:24:29 PM
 * Desc: 
 *
 */

package com.sun.sgs.manager;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.TimerHandle;
import com.sun.sgs.TimerListener;


/**
 * This manager provides access to the timer-related routines.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class TimerManager
{

    /**
     * Returns an instance of <code>TimerManager</code>.
     *
     * @return an instance of <code>TimerManager</code>
     */
    public static TimerManager getInstance() {
        // FIXME: return the instance
        return null;
    }

    /**
     * Registers an event to be run at a given time.
     *
     * @param delay the time in milliseconds to wait before starting
     * @param repeat whether to repeat the event each <code>delay</code>
     *               milliseconds
     * @param reference the listener to call to start the event
     */
    public abstract TimerHandle registerTimerEvent(long delay, boolean repeat,
            ManagedReference<? extends TimerListener> reference);

}
