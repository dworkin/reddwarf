/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

/**
 * Listener for protocol messages and session disconnection events.  A
 * service can register a {@code ProtocolMessageListener} and
 * associated service ID with the {@link ClientSessionService} in order
 * to be notified of protocol messages, received by client sessions,
 * that are destined for that service.  When a session becomes
 * disconnected, all registered {@code ProtocolMessageListener}s are
 * notified that that session is disconnected.
 *
 * @see ClientSessionService#registerProtocolMessageListener
 */
public interface ProtocolMessageListener {

    /**
     * Notifies this listener that the specified protocol
     * message has been received by the specified client session.
     *
     * @param	session a client session
     * @param	message a protocol messge
     */
    void receivedMessage(SgsClientSession session, byte[] message);

    /**
     * Notifies this listener that the specified client session has
     * become disconnected.
     *
     * @param	session a client session
     */
    void disconnected(SgsClientSession session);
}
