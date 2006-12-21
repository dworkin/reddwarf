package com.sun.sgs.io;

import java.net.SocketAddress;

/**
 * An {@code IOConnector} establishes connections, or 
 * {@code IOHandle}s, to remote hosts.  Clients should call shutdown()
 * when finished with the Connector.  Clients have the option to attach
 * {@code IOFilter}s on a per handle basis.  Filters allow for the manipulation
 * of the bytes before outgoing data is sent, and after incoming data is
 * received.
 * 
 * TODO: spec this to be non-reusable? -JM
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOConnector {

    
    /**
     * Attempts to connect to the remote host on the given port.  This call is
     * non-blocking, so a {@code connected()} callback will be generated on
     * the {@code listener} upon successful connection, or
     * {@code disconnected} if it fails.
     * 
     * @param address           the remote address to which to connect
     * @param listener          the <code>IOHandler</code> that will
     *                          receive the associated connection events.
     */
    public void connect(SocketAddress address, IOHandler listener);
    
    /**
     * Attempts to connect to the remote host on the given port.  This call is
     * non-blocking, so a {@code connected()} callback will be generated on
     * the {@code listener} upon successful connection, or
     * {@code disconnected} if it fails.  The given {@code IOFilter} will be
     * attached to the {@code IOHandle} upon connecting.
     * 
     * @param address           the remote address to which to connect
     * @param listener          the {@code IOHandler} that will
     *                          receive the associated connection events.
     * @param filter            the filter to attach to the connected 
     *                          {@code IOHandle}                          
     */
    public void connect(SocketAddress address, IOHandler listener, 
                            IOFilter filter);    
    
    
    /**
     * Shuts down this Connector.  Once shutdown, it cannot be restarted.
     * If there is a pending connection, it is aborted.
     */
    public void shutdown();
}
