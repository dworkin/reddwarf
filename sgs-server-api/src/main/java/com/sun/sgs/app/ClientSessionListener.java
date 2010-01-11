/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.app;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Listener for messages sent from an associated client session to the
 * server.
 *
 * <p>An implementation of a {@code ClientSessionListener} should
 * implement the {@link Serializable} interface, so that session
 * listeners can be stored persistently.  If a given listener has
 * mutable state, that listener should also implement the {@link
 * ManagedObject} interface.
 *
 * <p>The methods of this listener are called within the context of a
 * {@link Task} being executed by the {@link TaskManager}.  If, during
 * such an execution, a task invokes one of this listener's methods
 * and that method throws an exception, that exception implements
 * {@link ExceptionRetryStatus}, and its {@link
 * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
 * {@code true}, then the {@code TaskManager} will make
 * further attempts to retry the task that invoked the listener's
 * method.  It will continue those attempts until either an attempt
 * succeeds or it notices an exception is thrown that is not
 * retryable.
 *
 * <p>For a full description of task execution behavior, see the
 * documentation for {@link TaskManager#scheduleTask(Task)}.
 */
public interface ClientSessionListener {

    /**
     * Notifies this listener that the specified message, sent by the
     * associated session's client, was received.
     *
     * <p>If this listener needs to delay processing the given message
     * for any reason, including until more resources become
     * available, this listener may throw {@code MessageRejectedException}.
     * If this listener does throw {@code MessageRejectedException}, it
     * will be as if this invocation never happened; that is, the
     * transaction associated with this invocation is aborted, so it is not
     * possible to save any partial processing result before {@code
     * MessageRejectedException} is thrown.
     *
     * @param	message a message
     * @throws	MessageRejectedException if there are not enough resources
     *		to process the specified message
     */
    void receivedMessage(ByteBuffer message);

    /**
     * Notifies this listener that the associated session's client has
     * disconnected.  If {@code graceful} is {@code true}, then the
     * session's client logged out gracefully; otherwise, the session
     * was either disconnected forcibly by the server or disconnected
     * due to other factors such as communication failure. <p>
     *
     * If this listener does not implement {@link ManagedObject}, it will
     * be removed from the persistent store after this method returns.
     * Otherwise, this listener will remain in the persistent store until
     * it is explicitly {@linkplain DataManager#removeObject removed}. <p>
     *
     * When this method is invoked, the client session associated with this
     * listener will have already been removed if the application removed
     * the client session in order to disconnect it.  If the client session
     * has not yet been removed and this method does not remove the client
     * session, the client session will be removed in the same transaction
     * after this method returns.
     *
     * @param graceful if {@code true}, the specified client
     *        session logged out gracefully
     */
    void disconnected(boolean graceful);
}
