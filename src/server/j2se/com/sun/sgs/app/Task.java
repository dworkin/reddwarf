package com.sun.sgs.app;

import java.io.Serializable;

/**
 * Defines an application operation that will be run by the {@link
 * TaskManager}.  Classes that implement <code>Task</code> must also implement
 * {@link Serializable}.
 *
 * @see		TaskManager
 */
public interface Task {

    /**
     * Performs an action, throwing an exception if the action fails.
     *
     * @throws	Exception if the action fails
     */
    void run() throws Exception;
}
