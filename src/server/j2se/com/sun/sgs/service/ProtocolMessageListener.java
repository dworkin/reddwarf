package com.sun.sgs.service;

/**
 * Service listener for protocol messgages.
 */
public interface ProtocolMessageListener {

    /**
     * Notifies this listener that the specified protocol
     * message has been received by the specified client session.
     *
     * @param	session a client session
     * @param	message a protocol messge
     */
    void receivedMessage(SgsClientSession session, byte[] message) ;
}
