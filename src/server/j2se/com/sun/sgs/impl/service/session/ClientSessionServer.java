/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.Delivery;
import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for contacting the ClientSession server.
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
	byte[] sessionId, byte[] message, Delivery delivery)
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
