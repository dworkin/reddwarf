package com.sun.sgs.client;

import java.io.IOException;

public interface ClientConnector {
    /**
     * Initiates a non-blocking connect to this ClientConnector's target remote address.
     * TODO: more
     *
     * @param sessionListener The SessionListener that will receive events for the resulting Session
     *
     * @return a ConnectionHandle that can be used to cancel this connection attempt.
     * 
     * @throws AlreadyConnectedException if this channel is already connected 
     * @throws ConnectionPendingException if a non-blocking connection operation is already in progress on this channel 
     * @throws ClosedChannelException if this channel is closed 
     * @throws AsynchronousCloseException if another thread closes this channel while the connect operation is in progress 
     * @throws ClosedByInterruptException if another thread interrupts the current thread while the connect operation is in progress, thereby closing the channel and setting the current thread's interrupt status 
     * @throws UnresolvedAddressException if the given remote address is not fully resolved 
     * @throws UnsupportedAddressTypeException if the type of the given remote address is not supported 
     * @throws SecurityException if a security manager has been installed and it does not permit access to the given remote endpoint 
     * @throws IOException if some other I/O error occurs
     */
    ClientConnectionHandle connect(ServerSessionListener sessionListener) throws IOException;
}
