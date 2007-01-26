package com.sun.sgs.io;

/**
 * Receives asynchronous notification of connection events from an
 * associated {@link IOHandle}. The {@code connected} method is invoked
 * when the {@code IOHandle}'s connection is established, either actively
 * from an {@link IOConnector} or passively by an {@link IOAcceptor}. The
 * {@code bytesReceived} method is invoked when data arrives on the
 * connection. The {@code exceptionThrown} method is invoked to forward
 * asynchronous network exceptions. The {@code disconnected} method is
 * invoked when the connection has been closed, or if the connection could
 * not be initiated at all (e.g., when a connector fails to connect).
 */
public interface IOHandler {

    /**
     * Notifies this listener that the {@code IOHandle}'s connection is established,
     * either actively from an {@link IOConnector} or passively by an
     * {@link IOAcceptor}.  This indicates that the {@code handle} is
     * ready for use, and data may be sent and received on it.
     *
     * @param handle the {@code IOHandle} that has become connected.
     */
    void connected(IOHandle handle);

    /**
     * Notifies this listener that data arrives on a connection.
     * The {@code message} is not guaranteed to be a single, whole
     * message; this method is responsible for message reassembly
     * unless the {@code handle} itself guarantees that only complete
     * messages are delivered.
     *
     * @param handle the {@code IOHandle} on which the message arrived
     * @param message the received message bytes
     */
    void bytesReceived(IOHandle handle, byte[] message);

    /**
     * Notifies this listener that a network exception has occurred
     * on {@code handle}.
     *
     * @param handle the {@code IOHandle} on which the exception occured
     * @param exception the thrown exception
     */
    void exceptionThrown(IOHandle handle, Throwable exception);

    /**
     * Notifies this listener that the {@code handle}'s connection has been
     * closed, or that the connection could not be initiated (e.g., when a
     * connector fails to connect).
     *
     * @param handle the {@code IOHandle} that has disconnected
     */
    void disconnected(IOHandle handle);

}
