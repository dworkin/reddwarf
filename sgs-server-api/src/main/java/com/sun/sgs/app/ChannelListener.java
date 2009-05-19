/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.app;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Listener for messages received on a channel.  A channel can be
 * created with a {@code ChannelListener} which is notified when
 * any client session sends a message on that channel. 
 *
 * <p>An implementation of a {@code ChannelListener} should implement
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
 * {@code true}, then the {@code TaskManager} will make
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
     * Notifies this listener that the given {@code message} is being sent
     * by the specified {@code sender} on the given {@code channel}.  The
     * caller of this method does not automatically forward the message to
     * the channel for delivery.  If forwarding the message to the channel
     * is desired, the listener is responsible for doing so.
     *
     * <p>Depending on application-specific requirements, this listener may
     * take action, which is not limited to, but may include the following:
     * sending the message "as is" to the channel, sending alternate or
     * additional messages with or without the specified message content,
     * or ignoring the message.
     *
     * @param	channel a channel
     * @param	sender the sending client session
     * @param	message a message
     */
    void receivedMessage(
	Channel channel, ClientSession sender, ByteBuffer message);
}
