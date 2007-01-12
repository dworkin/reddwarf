package com.sun.sgs.io;

import java.net.SocketAddress;

/**
 * An {@code IOConnector} establishes connections, or 
 * {@code IOHandle}s, to remote hosts.  Clients should call shutdown()
 * when finished with the Connector.  Clients have the option to attach
 * {@code IOFilter}s on a per handle basis.  Filters allow for the manipulation
 * of the bytes before outgoing data is sent, and after incoming data is
 * received.
 * <p>
 * {@code IOConnector}s are created via {@code Endpoint.createConnector}.
 * 
 * <p>
 * An {@code IOConnector} is non-reusable in that subsequent calls to connect()
 * after the first call with throw {@code IllegalStateException}.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOConnector {
    
    /**
     * Attempts to connect to the remote host associated with this connector.
     * This call is non-blocking.  A {@code connected()} callback will be
     * generated on the {@code listener} upon successful connection, or
     * {@code disconnected} if it fails.
     * 
     * @param listener          the <code>IOHandler</code> that will
     *                          receive the associated connection events.
     */
    public void connect(IOHandler listener);
    
    /**
     * Attempts to connect to the remote host associated with this connector.
     * This call is non-blocking.  A {@code connected()} callback will be
     * generated on the {@code listener} upon successful connection, or
     * {@code disconnected} if it fails.
     * The given {@code IOFilter} will be attached to the {@code IOHandle}
     * upon connecting.
     * 
     * @param listener          the {@code IOHandler} that will
     *                          receive the associated connection events.
     * @param filter            the filter to attach to the connected 
     *                          {@code IOHandle}                          
     */
    public void connect(IOHandler listener, IOFilter filter);    
    
    
    /**
     * Shuts down this ConnectorShuts down this Connector, attempting to
     * cancel any connection attempt 
     * 
     * @throws IllegalStateException if there is no connection attempt
     *         in progress
     */
    public void shutdown();
}
