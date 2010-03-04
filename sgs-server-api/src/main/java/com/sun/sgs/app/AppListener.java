/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.app;

import java.io.Serializable;
import java.util.Properties;

/**
 * Listener for application-level events.  This listener is called
 * when the application is started for the first time, and when
 * client sessions log in.
 *
 * <p>An implementation of a {@code AppListener} should implement
 * the {@link Serializable} interface, so that application listeners
 * can be stored persistently.  When an application is started for the
 * the first time, its {@link #initialize(java.util.Properties) initialize}
 * method is called and it is then persisted.  If a given listener has mutable
 * state that can be changed after this point, it should also implement 
 * the {@link ManagedObject}
 * interface.  An implementation must be public and non-abstract, and
 * have a public, no-argument constructor.
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
public interface AppListener {

    /**
     * Notifies this listener that the application has been started
     * for the first time.  This gives the application an opportunity
     * to perform any necessary initialization.
     *
     * @param props application-specific configuration properties
     */
    void initialize(Properties props);

    /**
     * Notifies this listener that the specified client session has
     * logged in, and returns a {@link ClientSessionListener} for that
     * session.  The returned listener should implement {@link
     * Serializable} so that it can be stored persistently.  If the
     * returned listener does not implement {@code Serializable},
     * then the client session is disconnected without completing the
     * login process.
     *
     * <p>The returned {@code ClientSessionListener} is notified as
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
     * invoked with a {@code boolean} that is {@code true}
     * if the client logged out gracefully, and is {@code false}
     * otherwise.
     * </ul>
     *
     * <p>The {@code session} passed to this method is persisted in
     * the data manager.  The application may remove the {@code
     * session} from the data manager in order to disconnect the
     * session.  If the application does not remove the specified
     * client session, the client session is removed when the {@link
     * ClientSessionListener#disconnected disconnected} method invoked
     * on the returned listener completes.
     * 
     * <p>A return value of {@code null} has special meaning,
     * indicating that the specified client session should not
     * complete the login process and should be disconnected
     * immediately.
     *
     * @param session a client session
     * @return a (serializable) listener for the client session,
     * or {@code null} to indicate that the session should
     * be terminated without completing the login process.
     */
    ClientSessionListener loggedIn(ClientSession session);
}
