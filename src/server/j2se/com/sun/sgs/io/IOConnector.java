package com.sun.sgs.io;

import java.net.SocketAddress;

/**
 * An {@code IOConnector} establishes connections, or {@link IOHandle}s, to
 * remote hosts. Clients should call shutdown() when finished with the
 * Connector. Clients have the option to attach {@link IOFilter}s on a per
 * handle basis. Filters allow for the manipulation of the bytes before
 * outgoing data is sent, and after incoming data is received. <p>
 * {@code IOConnector}s are created via {@link Endpoint#createConnector}.
 * <p> An {@code IOConnector} is non-reusable in that subsequent calls to
 * connect() after the first call with throw {@link IllegalStateException}.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOConnector<T> {

    /**
     * Attempts to connect to the remote host associated with this
     * connector. This call is non-blocking. {IOHandler#connected()} will be
     * called on the {@code listener} upon successful connection, or
     * {@link IOHandler#disconnected} if it fails.
     * 
     * @param listener the <code>IOHandler</code> that will receive the
     *        associated connection events.
     */
    void connect(IOHandler listener);

    /**
     * Attempts to connect to the remote host associated with this
     * connector. This call is non-blocking. {IOHandler#connected()} will be
     * called on the {@code listener} upon successful connection, or
     * {@link IOHandler#disconnected} if it fails. The given
     * {@link IOFilter} will be attached to the {@link IOHandle} upon
     * connecting.
     * 
     * @param listener the {@link IOHandler} that will receive the
     *        associated connection events.
     * @param filter the filter to attach to the connected {@link IOHandle}
     */
    public void connect(IOHandler listener, IOFilter filter);

    /**
     * Shuts down this {@code IOConnector}, attempting to cancel any
     * connection attempt in progress.
     * 
     * @throws IllegalStateException if there is no connection attempt in
     *         progress
     */
    void shutdown();

    /**
     * Returns the {@link Endpoint} to which this {@code IOConnector} will
     * connect.
     * 
     * @return the {@link Endpoint} to which this {@code IOConnector} will
     *         connect.
     */
    Endpoint<T> getEndpoint();
}
