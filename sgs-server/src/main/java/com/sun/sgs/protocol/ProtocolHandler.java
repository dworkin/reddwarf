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
 * A handler for session and channel protocol messages for an associated
 * client session.
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
public interface ProtocolHandler extends ChannelProtocolHandler {

    /**
     * Processes a login request with the specified {@code name}, and
     * {@code password}.
     *
     * <p>When this handler has completed processing the login request, it
     * invokes the returned future's {@link CompletionFuture#done done}
     * method to notify the caller that the request has been processed.
     *
     * @param	name a user name
     * @param	password a password
     * @return	future a future to be notified when the request has been
     *		processed
     */
    CompletionFuture loginRequest(String name, String password);

    /**
     * Processes a message sent by the associated client.
     *
     * <p>When this handler has completed processing the session message,
     * it invokes the returned future's {@link CompletionFuture#done done}
     * method to notify the caller that the request has been processed.
     *
     * @param	message a message
     * @return	future a future to be notified when the request has been
     *		processed
     */
    CompletionFuture sessionMessage(ByteBuffer message);

    /**
     * Processes a logout request from the associated client.
     *
     * <p>When this handler has completed processing the logout request,
     * it invokes the returned future's {@link CompletionFuture#done done}
     * method to notify the caller that the request has been processed.
     *
     * @return	future a future to be notified when the request has been
     *		processed
     */
    CompletionFuture logoutRequest();

    /**
     * Processes disconnection request.  This method is used to indicate
     * that a non-graceful disconnect from the client has occurred.
     *
     * <p>When this handler has completed processing the disconnection,
     * it invokes the returned future's {@link CompletionFuture#done done}
     * method to notify the caller that the request has been processed.
     *
     * @return	future a future to be notified when the request has been
     *		processed
     */
    CompletionFuture disconnect();
}
