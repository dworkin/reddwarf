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
import java.util.concurrent.ExecutionException;

/**
 * A handler for session and channel protocol messages for an associated
 * client session.
 *
 * Each operation returns a {@code Future} that indicates when the
 * request has been processed .  A caller may need to know when an
 * operation has completed so that it can throttle incoming messages (for
 * example only resuming reading when the handler completes processing a
 * request), and/or can control the number of clients connected at any given
 * time.
 */
public interface SessionProtocolHandler {

    /**
     * Processes a message sent by the associated client and returns a
     * future for determining when this handler has completed processing
     * the session message.
     *
     * @param	message a message
     * @return	future a future for the result
     */
    CompletionFuture sessionMessage(ByteBuffer message);

    /**
     * Processes a channel message sent by the associated client on the
     * channel with the specified {@code channelId}, and returns a future
     * for determining when this handler has completed processing the
     * channel message.
     *
     * @param	channelId a channel ID
     * @param	message a message
     * @return	future a future for the result
     */
    CompletionFuture channelMessage(BigInteger channelId, ByteBuffer message);
    
    /**
     * Processes a logout request from the associated client and returns a
     * future for determining when this handler has completed processing
     * the logout request.
     *
     * @return	future a future for the result
     */
    CompletionFuture logoutRequest();

    /**
     * Notifies this handler that a non-graceful client disconnection has
     * occurred, and returns a future for determining when the handler has
     * completed processing the disconnection.
     *
     * @return	future a future for the result
     */
    CompletionFuture disconnect();
}
