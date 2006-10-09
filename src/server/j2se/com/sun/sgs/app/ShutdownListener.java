package com.sun.sgs.app;

import java.io.Serializable;

/**
 * Listener for application shutdown completion.
 *
 * <p>An instance of this listener is passed to the {@link
 * AppListener#shuttingDown(ShutdownListener, boolean)
 * AppListener.shuttingDown} method.  When the application completes
 * shutting down, it must call the supplied
 * <code>ShutdownListener</code>'s {@link #shutdownComplete
 * shutdownComplete} method.  Note: An application is not permitted to
 * start up again until a previous shutdown request has been
 * completed.  Therefore, failure to notify this listener of shutdown
 * completion (by invoking its {@link #shutdownComplete
 * shutdownComplete} method) will prevent the application from
 * starting up after a shutdown.
 *
 * <p>An implementation of a <code>ShutdownListener</code> should implement
 * the {@link Serializable} interface, so that shutdown listeners
 * can be stored persistently.  If a given listener has mutable state,
 * that listener should also implement the {@link ManagedObject}
 * interface.
 *
 * <p>The methods of this listener are called within the context of a
 * {@link Task} being executed by the {@link TaskManager}.  If, during
 * such an execution, a task invokes one of this listener's methods
 * and that method throws an exception, that exception implements
 * {@link ExceptionRetryStatus}, and its {@link
 * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
 * <code>true</code>, then the <code>TaskManager</code> will make
 * further attempts to retry the task that invoked the listener's
 * method.  It will continue those attempts until either an attempt
 * succeeds or it notices an exception is thrown that is not
 * retryable.
 *
 * <p>For a full description of task execution behavior, see the
 * documentation for {@link TaskManager#scheduleTask(Task)}.
 */
public interface ShutdownListener {

    /**
     * Notifies this listener that the application has completed
     * shutting down.
     */
    void shutdownComplete();
}
