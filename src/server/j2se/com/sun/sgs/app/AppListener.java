package com.sun.sgs.app;

import java.io.Serializable;
import java.util.Properties;

/**
 * Listener for application-level events.  This listener is called
 * when the application starts up, when client sessions log in, and
 * when the application is shutting down.
 *
 * <p>An implementation of a <code>AppListener</code> should implement
 * the {@link Serializable} interface, so that application listeners
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
public interface AppListener extends ManagedObject {

    /**
     * Notifies this listener that the application is starting up.
     * This gives the application an opportunity to perform any
     * necessary initialization.
     *
     * @param props application-specific configuration properties
     */
    void startingUp(Properties props);

    /**
     * Notifies this listener that the specified client session has
     * logged in, and returns a {@link ClientSessionListener} for that
     * session.  The returned listener should implement {@link Serializable}
     * so that it can be stored persistently.
     *
     * <p>The returned <code>ClientSessionListener</code> is notified as
     * follows:<ul>
     *
     * <li>If a message is received from the specified client session,
     * the returned listener's {@link
     * ClientSessionListener#receivedMessage receivedMessage} method
     * is invoked with the message.
     *
     * <li>If the specified client session logs out or becomes
     * disconnected for other reasons, the returned listener's {@link
     * ClientSessionListener#disconnected disconnected} method is
     * invoked with a <code>boolean</code> that is <code>true</code>
     * if the client logged out gracefully, and is <code>false</code>
     * otherwise.
     * </ul>
     * 
     * <p>A return value of <code>null</code> has special meaning,
     * indicating that the specified client session should not
     * complete the login process and should be disconnected
     * immediately.
     *
     * @param session a client session
     * @return a (serializable) listener for the client session,
     * or <code>null</code> to indicate that the session should
     * be terminated without completing the login process.
     */
    ClientSessionListener loggedIn(ClientSession session);
}
