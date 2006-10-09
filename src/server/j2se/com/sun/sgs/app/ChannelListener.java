package com.sun.sgs.app;

import java.io.Serializable;

/**
 * Listener for messages sent on a channel.  A channel can be created
 * with a <code>ChannelListener</code> which is notified when any
 * client session sends a message on that channel.  Additionally, a
 * server can register a per-session listener (to be notified when
 * messages are sent by an individual client sessions on a channel) by
 * invoking the channel's {@link Channel#setListener setListener}
 * method with the channel listener and the session.
 *
 * <p>An implementation of a <code>ChannelListener</code> should implement
 * the {@link Serializable} interface, so that channel listeners
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
public interface ChannelListener {

    /**
     * Notifies this listener that the specified message, sent on the
     * specified channel by the specified session, was received.
     *
     * @param channel a channel
     * @param session a session
     * @param message byte array containing a message
     */
    void receivedMessage(Channel channel, Session session, byte[] message);
    
}
