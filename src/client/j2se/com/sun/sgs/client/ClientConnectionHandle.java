package com.sun.sgs.client;

public interface ClientConnectionHandle {
    /**
     * Cancels a pending connect operation on the ClientConnector that
     * created this ConnectionHandle.
     *
     * @throws AlreadyConnectedException if the connection has already finished 
     */
    void cancel();
}
