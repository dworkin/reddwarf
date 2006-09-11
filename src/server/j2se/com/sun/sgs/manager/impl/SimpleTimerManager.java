
package com.sun.sgs.manager.impl;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.TimerHandle;

import com.sun.sgs.manager.TimerManager;

import com.sun.sgs.manager.listen.TimerListener;

import com.sun.sgs.service.TimerService;


/**
 * This is a simple implementation of <code>TimerManager</code> that is the
 * default used.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleTimerManager extends TimerManager
{

    // the backing timer service
    private TimerService timerService;

    /**
     * Creates an instance of <code>SimpleTimerManager</code>.
     *
     * @param timerService the backing service
     */
    public SimpleTimerManager(TimerService timerService) {
        super();

        this.timerService = timerService;
    }

    /**
     * Registers an event to be run at a given time.
     *
     * @param delay the time in milliseconds to wait before starting
     * @param repeat whether to repeat the event each <code>delay</code>
     *               milliseconds
     * @param reference the listener to call to start the event
     */
    public TimerHandle registerTimerEvent(long delay, boolean repeat,
            ManagedReference<? extends TimerListener> reference) {
        return timerService.registerTimerEvent(delay, repeat, reference);
    }

}
