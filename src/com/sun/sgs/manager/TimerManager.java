
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

    // the singleton instance of TimerManager
    private static TimerManager manager = null;

    /**
     * Creates an instance of <code>TimerManager</code>. This class enforces
     * a singleton model, so only one instance of <code>TimerManager</code>
     * may exist in the system.
     *
     * @throws IllegalStateException if an instance already exists
     */
    protected TimerManager() {
        if (manager != null)
            throw new IllegalStateException("TimerManager is already " +
                                            "initialized");

        manager = this;
    }

    /**
     * Returns the instance of <code>TimerManager</code>.
     *
     * @return the instance of <code>TimerManager</code>
     */
    public static TimerManager getInstance() {
        return manager;
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
