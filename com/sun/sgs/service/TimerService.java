
/*
 * TimerService.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Jul 13, 2006	 7:48:05 PM
 * Desc: 
 *
 */

package com.sun.sgs.service;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.TimerHandle;
import com.sun.sgs.TimerListener;


/**
 * This type of <code>Service</code> handles timed events.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface TimerService extends Service
{

    /**
     * Registers an event to be run at a given time.
     *
     * @param txn the <code>Transaction</code> state
     * @param delay the time in milliseconds to wait before starting
     * @param repeat whether to repeat the event each <code>delay</code>
     *               milliseconds
     * @param reference the listener to call to start the event
     */
    public abstract TimerHandle registerTimerEvent(Transaction txn,
            long delay, boolean repeat,
            ManagedReference<? extends TimerListener> reference);

}
