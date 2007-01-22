package com.sun.sgs.service;

/**
 * Listener for protocol messgages.  A service can register a
 * <code>ProtocolMessageListener</code> and associate service ID with
 * the {@link ClientSessionService} in order to be notified of
 * protocol messages received by client sessions that are destined for
 * that service.
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
    void receivedMessage(SgsClientSession session, byte[] message) ;
}
