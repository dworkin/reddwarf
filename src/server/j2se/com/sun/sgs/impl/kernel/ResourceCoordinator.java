
package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.kernel.TaskThread;


/**
 * This interface defines the class responsible for managing access to
 * all resources (threads, transaction state, etc.) in the system.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface ResourceCoordinator
{

    /**
     * Gives a thread back to the system.
     *
     * @param taskThread the thread being returned
     */
    public void giveThread(TaskThread taskThread);

    /**
     * Requests a thread from the system.
     *
     * @return a thread
     */
    public TaskThread requestThread();

}
