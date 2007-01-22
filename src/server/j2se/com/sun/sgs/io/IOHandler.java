package com.sun.sgs.io;

/**
 * Receives asynchronous notification of connection events from an
 * associated {@link IOHandle}.  The {@code connected} method is
 * invoked when the {@code IOHandle}'s connection is established,
 * either actively from an {@link IOConnector} or passively by an
 * {@link IOAcceptor}.  The {@code bytesReceived} method is invoked
 * when data arrives on the connection (after being processed by
 * the {@link IOFilter} associated with the handle).  The
 * {@code exceptionThrown} method is invoked to forward asynchronous
 * network exceptions.  The {@code disconnected} method is invoked
 * when the connection has been closed, or if the connection could
 * not be initiated at all (e.g., when a connector fails to connect).
 *
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOHandler {

    /**
     * Invoked when the {@code IOHandle}'s connection is established,
     * either actively from an {@link IOConnector} or passively by an
     * {@link IOAcceptor}.  This indicates that the {@code handle} is
     * ready for use, and data may be sent and received on it.
     *
     * @param handle the {@code IOHandle} that has become connected.
     */
    void connected(IOHandle handle);

    /**
     * Invoked when data arrives on a connection, after processing by
     * the {@link IOFilter} associated with the {@code handle}.  The
     * {@code message} is not guaranteed to be a single, whole message,
     * unless the {@code handle}'s {@code IOFilter} makes that guarantee.
     * Otherwise, implementations of {@code IOHandle} are responsible for
     * their own application-level message framing.
     * <p>
     * Note: The filter may modify the incoming data in any way,
     * and may invoke this once, multiple times, or not at all for
     * a particlar receive event on the connection.
     *
     * @param handle the {@code IOHandle} on which the data arrived.
     * @param message the received, filtered data.
     */
    void bytesReceived(IOHandle handle, byte[] message);

    /**
     * Notifies this listener of a network exception on {@code handle}.
     * @param handle the {@code IOHandle} on which the exception occured.
     * @param exception the thrown exception.
     */
    void exceptionThrown(IOHandle handle, Throwable exception);

    /**
     * Invoked when the {@code handle}'s connection has been closed, or if
     * the connection could not be initiated (e.g., when a connector fails
     * to connect).
     *
     * @param handle the {@code IOHandle} that has disconnected.
     */
    void disconnected(IOHandle handle);
}
