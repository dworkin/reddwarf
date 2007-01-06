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
     * Immediately sends the specified protocol message to this
     * session's client with the specified delivery guarantee.
     *
     * @param message a complete protocol message
     * @param delivery a delivery requirement
     */
    void sendMessage(byte[] message, Delivery delivery);

    /**
     * Sends the message in the specified byte array to this
     * session's client when the current transaction commits.
     *
     * @param message a complete protocol message
     * @param delivery a delivery requirement
     */
    void sendMessageOnCommit(byte[] message, Delivery delivery);
}
