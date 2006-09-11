
package com.sun.sgs.service;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.TimerHandle;

import com.sun.sgs.app.listen.TimerListener;


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
     * @param delay the time in milliseconds to wait before starting
     * @param repeat whether to repeat the event each <code>delay</code>
     *               milliseconds
     * @param reference the listener to call to start the event
     */
    public abstract TimerHandle registerTimerEvent(
            long delay, boolean repeat,
            ManagedReference<? extends TimerListener> reference);

}
