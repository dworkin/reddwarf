package com.sun.sgs.service;

import java.math.BigInteger;

import com.sun.sgs.app.ClientSession;

/**
 * A listener that services may register with the {@link ClientSessionService}
 * to receive notification of session disconnect.
 * 
 * @see ClientSessionService#registerSessionDisconnectListener
 */
public interface ClientSessionDisconnectListener {

    /**
     * Notifies this listener that the session with the given
     * {@code sessionRefId} has disconnected.
     * 
     * @param sessionRefId the ID of the {@code ManagedReference} to the
     *        {@link ClientSession} that disconnected
     */
    void disconnected(BigInteger sessionRefId);
}
