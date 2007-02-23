package com.sun.sgs.service;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.auth.Identity;

/**
 * A representation of a {@link ClientSession} used to send protocol
 * messages to a session's client.
 */
public interface SgsClientSession extends ClientSession {
    /**
     * Returns the {@link Identity} used to authenticate this session, or
     * {@code null} if the session is not authenticated.
     *
     * @return the {@code Identity} used to authenticate this session, or
     *         {@code null} if the session is not authenticated
     */
    Identity getIdentity();

    /**
     * Sends (with the specified delivery guarantee) the specified
     * protocol message to this session's client.  This method is not
     * transactional, and therefore this message send cannot be
     * aborted.
     *
     * @param message a complete protocol message
     * @param delivery a delivery requirement
     */
    void sendProtocolMessage(byte[] message, Delivery delivery);

    /**
     * Sends (with the specified delivery guarantee) the specified
     * protocol message to this session's client when the current
     * transaction commits.
     *
     * @param message a complete protocol message
     * @param delivery a delivery requirement
     */
    void sendProtocolMessageOnCommit(byte[] message, Delivery delivery);
}
