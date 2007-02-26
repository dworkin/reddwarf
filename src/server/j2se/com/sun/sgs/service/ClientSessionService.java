/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

/**
 * The client session service manages client sessions.
 */
public interface ClientSessionService extends Service {

    /**
     * Registers the specified protocol message listener for the
     * specified service ID.
     *
     * <p>When a client session receives a protocol message with the
     * specified service ID, the specified listener's {@link
     * ProtocolMessageListener#receivedMessage receivedMessage} method is
     * invoked with the {@link SgsClientSession client session} and
     * the complete protocol message.
     *
     * <p>The reserved service IDs are 0-127.  The current ones in use are:
     * <ul>
     * <li> <code>0x01</code>, application service
     * <li> <code>0x02</code>, channel service
     * </ul>
     *
     * @param serviceId a service ID
     * @param listener a protocol message listener
     */
    void registerProtocolMessageListener(
	byte serviceId, ProtocolMessageListener listener);

    /**
     * Returns the client session corresponding to the specified
     * session ID, or <code>null</code> if there is no existing client
     * session for the specified ID.
     *
     * @param sessionId a session ID
     * @return a client session, or <code>null</code>
     */
    SgsClientSession getClientSession(byte[] sessionId);
}
