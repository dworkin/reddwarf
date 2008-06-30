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

package com.sun.sgs.app;

import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Interface representing a single, connected login session between a
 * client and the server.  Classes that implement
 * {@code ClientSession} must also implement {@link Serializable}.
 *
 * <p>When a client logs in, the application's {@link
 * AppListener#loggedIn(ClientSession) AppListener.loggedIn} method is
 * invoked with a new {@code ClientSession} instance which represents the
 * current connection between that client and the server.  By returning a
 * unique {@link ClientSessionListener} from the {@code loggedIn} method
 * for each given client session, the application will receive notification
 * when a client session sends a message, is disconnected, or logs out.
 *
 * <p>A {@code ClientSession} is used to identify a client that is
 * logged in, to send messages to that client, and to forcibly disconnect
 * that client from the server.
 *
 * <p>A session is considered disconnected if one of the following occurs:
 * <ul>
 * <li> the client logs out
 * <li> the client becomes disconnected due to a network failure, and
 * a connection to the client cannot be re-established in a timely manner
 * <li> the {@code ClientSession} object is removed from the data manager
 * </ul>
 *
 * <p>If a client associated with a {@code ClientSession} becomes
 * disconnected due to one of these conditions, the {@link
 * ClientSessionListener#disconnected(boolean) disconnected} method is
 * invoked on that session's registered
 * {@code ClientSessionListener} with a {@code boolean} that
 * if {@code true} indicates the client logged out gracefully.
 *
 * <p>Once a client becomes disconnected, its {@code ClientSession}
 * becomes invalid and can no longer be used to communicate with that
 * client and should be removed from the data manager. When that client
 * logs back in again, a new session is established with the server.
 */
public interface ClientSession extends ManagedObject {

    /**
     * Returns the login name used to authenticate this session.
     *
     * @return	the name used to authenticate this session
     *
     * @throws	IllegalStateException if this session is disconnected
     * @throws 	TransactionException if the operation failed because of
     * 		a problem with the current transaction
     */
    String getName();
    
    /**
     * Sends a message contained in the specified {@link ByteBuffer} to
     * this session's client. The message starts at the buffer's current
     * position and ends at the buffer's limit.  The buffer's position is
     * not modified by this operation.
     * 
     * <p>The {@code ByteBuffer} may be reused immediately after this method
     * returns.  Changes made to the buffer after this method returns will
     * have no effect on the message sent to the client by this invocation.
     *
     * @param	message a message
     *
     * @return	this client session
     *
     * @throws	IllegalStateException if this session is disconnected
     * @throws	IllegalArgumentException if the message exceeds the allowed
     *		payload length defined by the constant {@link
     *		com.sun.sgs.protocol.simple.SimpleSgsProtocol#MAX_PAYLOAD_LENGTH}
     *		whose value is {@value
     *		com.sun.sgs.protocol.simple.SimpleSgsProtocol#MAX_PAYLOAD_LENGTH} 
     * @throws	MessageRejectedException if there are not enough resources
     *		to send the specified message
     * @throws	TransactionException if the operation failed because of
     *		 a problem with the current transaction
     */
    ClientSession send(ByteBuffer message);

    /**
     * Returns {@code true} if the client is connected,
     * otherwise returns {@code false}.
     *
     * @return {@code true} if the client is connected,
     * 		otherwise returns {@code false}
     *
     * @throws	TransactionException if the operation failed because of
     * 		a problem with the current transaction
     */
    boolean isConnected();
}
