
/*
 * EventManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Jul 13, 2006	 7:59:14 PM
 * Desc: 
 *
 */

package com.sun.sgs.manager;

import com.sun.sgs.ManagedRunnable;
import com.sun.sgs.Quality;


/**
 * This interface is the entry point for all events in the system. 
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface EventQueue
{

    /**
     * Queue an event to run when the resources are available.
     * <p>
     * FIXME: This also takes some meta-data.
     *
     * @param r the task to run
     * @param quality the desired quality of service parameters
     */
    public void queueEvent(ManagedRunnable r, Quality quality);

}
