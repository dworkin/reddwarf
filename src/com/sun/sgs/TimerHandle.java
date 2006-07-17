
/*
 * TimerHandle.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Jul 14, 2006	 1:47:28 PM
 * Desc: 
 *
 */

package com.sun.sgs;


/**
 * This interface provides a handle to some timed event. The event may
 * may be paused and re-started, or cancelled completely. This is
 * especially useful for long-running, repeating tasks.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface TimerHandle extends ManagedReference
{

    /**
     * Suspends the timed event. If the event has been suspended, cancelled,
     * or has completed (and is not recurring), then this method has no
     * effect and returns false.
     *
     * @return true if the event is suspended, false otherwise
     */
    public boolean suspend();

    /**
     * Resumes the timed event. If the event is not suspended, is cancelled,
     * or has completed (and is not recurring), then this method has no
     * effect and returns false.
     *
     * @return true if the event is resumed, false otherwise
     */
    public boolean resume();

    /**
     * Cancels this event. If the event has already been cancelled, or has
     * completed (and is not recurring), then this method has no effect and
     * returns false.
     *
     * @return true if the event is cancelled, false otherwise.
     */
    public boolean cancel();

}
