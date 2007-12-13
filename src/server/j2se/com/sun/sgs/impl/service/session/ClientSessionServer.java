/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.Delivery;
import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for communicating between peer client session
 * service implementations.
 */
public interface ClientSessionServer extends Remote {

    /**
     * Returns {@code true} if the client session with the
     * specified {@code sessionId} is connected, otherwise returns
     * {@code false}.
     *
     * @param	sessionId a session ID
     * @return	{@code true} if the client session with the specified
     * 		{@code sessionId} is connected to this server, otherwise
     *		{@code false}
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean isConnected(byte[] sessionId)
	throws IOException;
    
    /**
     * If a client session with the specified {@code sessionId} is
     * connected to this server, sends the specified protocol {@code
     * message} according to the specified {@code delivery} and
     * returns {@code true}.  If a client session with the specified
     * {@code sessionId} is not connected to this server, the message
     * is not sent, and {@code false} is returned.
     *
     * @param	sessionId a session ID
     * @param	message a protocol message
     * @param	delivery a delivery requirement
     * @return	{@code true} if the client session with the specified
     * 		{@code sessionId} is connected to this server, otherwise
     *		{@code false}
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean sendProtocolMessage(
	byte[] sessionId, long seq, byte[] message, Delivery delivery)
	throws IOException;

    /**
     * If a client session with the specified {@code sessionId} is
     * connected to this server, sends the specified protocol {@code
     * messages} according to the specified {@code delivery}
     * requirements, and returns {@code true}.  If a client session
     * with the specified {@code sessionId} is not connected to this
     * server, the messages are not sent, and {@code false} is
     * returned.
     *
     * @param	sessionId a session ID
     * @param	messages an array of protocol messages, each contained
     *		in a byte array
     * @param	delivery an array of delivery requirements
     * @return	{@code true} if the client session with the specified
     * 		{@code sessionId} is connected to this server, otherwise
     *		{@code false}
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean sendProtocolMessages(byte[] sessionId,
				 long[] seq,
				 byte[][] messages,
				 Delivery[] delivery)
	throws IOException;
    
    /**
     * If a client session with the specified {@code sessionId} is
     * connected to this server, disconnects the client session and
     * returns {@code true}.  If a client session with the specified
     * {@code sessionId} is not connected to this server, {@code false}
     * is returned.
     *
     * @param	sessionId a session ID
     * @return	{@code true} if the client session with the specified
     * 		{@code sessionId} is connected to this server, otherwise
     *		{@code false}
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean disconnect(byte[] sessionId)
	throws IOException;
}
