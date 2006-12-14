package com.sun.sgs.service;

import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;

/**
 * These operations are only supported in a non-transactional context.
 */
public interface SgsClientSession extends ClientSession {

    /**
     * Returns the next sequence number for this client session.
     */
    long nextSequenceNumber();

    /**
     * Sends the specified protocol message to this session's client
     * with the specified delivery guarantee.
     *
     * @param message a complete protocol message
     * @param delivery a delivery requirement
     */
    void sendMessage(byte[] message, Delivery delivery);
}
