/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.protocol;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * A handler for protocol messages for an associated client session.
 *
 * Each operation has a {@code CompletionFuture} argument that, if
 * non-null, must be notified when the corresponding operation has been
 * processed.  A caller may require notification of operation completion
 * so that it can perform some throttling, for example only resuming
 * reading when a protocol message has been processed by the handler, or
 * controlling the number of clients connected at any given time.
 *
 * <p>TBD: should reconnection be handled a this layer or transparently by
 * the transport layer?
 */
public interface ProtocolMessageHandler {

    /**
     * Processes a login request with the specified protocol {@code
     * version}, {@code name}, and {@code password}.
     *
     * <p>When this handler has completed processing the login request, it
     * must invoke the given {@code future}'s {@link CompletionFuture#done
     * done} method to notify the caller that the request has been
     * processed.
     *
     * <p>TBD: Does protocol version belong at this layer?  Seems like it
     * should be handled by the {@code ProtocolMessageChannel}.
     *
     * @param	version a protocol version
     * @param	name a user name
     * @param	password a password
     * @param	future a future to be notified when the request has been
     *		processed, or {@code null}
     */
    void loginRequest(int version,  String name, String password,
		      CompletionFuture future);

    /**
     * Processes a message sent by the associated client.
     *
     * <p>When this handler has completed processing the session message, it
     * must invoke the given {@code future}'s {@link CompletionFuture#done
     * done} method to notify the caller that the request has been
     * processed.
     *
     * @param	message a message
     * @param	future a future to be notified when the request has been
     *		processed, or {@code null}
     */
    void sessionMessage(ByteBuffer message, CompletionFuture future);

    /**
     * Processes a channel message sent by the associated client on the
     * channel with the specified {@code channelId}.
     *
     * <p>When this handler has completed processing the channel message, it
     * must invoke the given {@code future}'s {@link CompletionFuture#done
     * done} method to notify the caller that the request has been
     * processed.
     *
     * @param	channelId a channel ID
     * @param	message a message
     * @param	future a future to be notified when the request has been
     *		processed, or {@code null}
     */
    void channelMessage(BigInteger channelId, ByteBuffer messsage,
			CompletionFuture future);

    /**
     * Processes a logout request from the associated client.
     *
     * <p>When this handler has completed processing the channel message, it
     * must invoke the given {@code future}'s {@link CompletionFuture#done
     * done} method to notify the caller that the request has been
     * processed.
     *
     * @param	future a future to be notified when the request has been
     *		processed, or {@code null}
     */
    void logoutRequest(CompletionFuture future);

    /**
     * Notifies this handler that the associated client is disconnected.
     *
     * @param	future a future to be notified when the request has been
     *		processed, or {@code null}
     */
    void disconnected(CompletionFuture future);
}
