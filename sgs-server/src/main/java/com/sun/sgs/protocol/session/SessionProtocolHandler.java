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

package com.sun.sgs.protocol.session;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.protocol.CompletionFuture;
import com.sun.sgs.protocol.ProtocolHandler;
import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * A handler for session and channel protocol messages for an associated
 * client session.
 *
 * Each operation returns a {@code CompletionFuture} that
 * must be notified when the corresponding operation has been
 * processed.  A caller may require notification of operation completion
 * so that it can perform some throttling, for example only resuming
 * reading when a protocol message has been processed by the handler, or
 * controlling the number of clients connected at any given time.
 *
 * <p>TBD: should reconnection be handled a this layer or transparently by
 * the transport layer?
 */
public interface SessionProtocolHandler extends ProtocolHandler {

    /**
     * Processes a login request with the specified
     * {@code authenticatedIdentity}.
     *
     * <p>When this handler has completed processing the login request, it
     * uses the returned future to notify the caller that the request has
     * been processed.
     *
     * @param	authenticatedIdentity the authenticated identity
     * @return	future a future to be notified when the request has been
     *		processed
     */
    CompletionFuture loginRequest(Identity authenticatedIdentity);

    /**
     * Processes a message sent by the associated client.
     *
     * <p>When this handler has completed processing the session message,
     * it uses the returned future to notify the caller that the request
     * has been processed.
     *
     * @param	message a message
     * @return	future a future to be notified when the request has been
     *		processed
     */
    CompletionFuture sessionMessage(ByteBuffer message);

    /**
     * Processes a channel message sent by the associated client on the
     * channel with the specified {@code channelId}.
     *
     * <p>When this handler has completed processing the channel message,
     * it uses the returned future to notify the caller that the request
     * has been processed.
     *
     * @param	channelId a channel ID
     * @param	message a message
     * @return	future a future to be notified when the request has been
     *		processed
     */
    CompletionFuture channelMessage(BigInteger channelId, ByteBuffer message);

    /**
     * Processes a logout request from the associated client.
     *
     * <p>When this handler has completed processing the logout request,
     * it uses the returned future to notify the caller that the request
     * has been processed.
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
     * it usess the returned future to notify the caller that the request
     * has been processed.
     *
     * @return	future a future to be notified when the request has been
     *		processed
     */
    CompletionFuture disconnect();
}
