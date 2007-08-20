/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.service;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionManager;
import com.sun.sgs.app.Delivery;

/**
 * The client session service manages client sessions.
 */
public interface ClientSessionService extends ClientSessionManager, Service {

    /**
     * Registers the specified protocol message listener for the
     * specified service ID.  This method is non-transactional and
     * should be called outside of a transaction.
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
     * session ID, or (@code null} if there is no existing client
     * session for the specified ID.  This method should be called
     * within a transaction.
     *
     * @param	sessionId a session ID
     *
     * @return	a client session, or <code>null</code>
     *
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    ClientSession getClientSession(byte[] sessionId);

    /**
     * Sends the specified protocol {@code message} to the specified
     * client {@code session} with the specified {@code delivery}
     * guarantee.  This method must be called within a transaction.
     * The message is delivered when the transaction commits.
     *
     * @param	session	a client session
     * @param	message a complete protocol message
     * @param	delivery a delivery requirement
     *
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    void sendProtocolMessage(
	ClientSession session, byte[] message, Delivery delivery);

    /**
     * Sends the specified protocol {@code message} to the specified
     * client {@code session} with the specified {@code delivery}
     * guarantee.  This method is non-transactional, and therefore
     * this message send cannot be aborted.
     *
     * @param	session	a client session
     * @param	message a complete protocol message
     * @param	delivery a delivery requirement
     */
    void sendProtocolMessageNonTransactional(
	ClientSession session, byte[] message, Delivery delivery);
    
    /**
     * Disconnects the specified client {@code session}.
     *
     * @param	session a client session
     */
    void disconnect(ClientSession session);
}
