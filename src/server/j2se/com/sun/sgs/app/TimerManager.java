
package com.sun.sgs.app;

import com.sun.sgs.kernel.TaskThread;

import com.sun.sgs.app.listen.TimerListener;


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
     * Creates an instance of <code>TimerManager</code>.
     */
    protected TimerManager() {

    }

    /**
     * Returns the instance of <code>TimerManager</code>.
     *
     * @return the instance of <code>TimerManager</code>
     */
    public static TimerManager getInstance() {
        return ((TaskThread)(Thread.currentThread())).getTask().
            getAppContext().getTimerManager();
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
