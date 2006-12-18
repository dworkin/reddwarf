package com.sun.sgs.io;

import java.io.IOException;
import java.net.InetAddress;

/**
 * An {@code IOConnector} establishes connections, or 
 * {@code IOHandle}s, to remote hosts.  Clients should call shutdown()
 * when finished with the Connector.  Clients have the option to attach
 * {@code IOFilter}s on a per handle basis.  Filters allow for the manipulation
 * of the bytes before outgoing data is sent, and after incoming data is
 * received.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOConnector {

    
    /**
     * Attempts to connect to the remote host on the given port.  This call is
     * non-blocking, so the returned <code>IOHandle</code> should not be 
     * considered live until the <code>IOHandler.connected()</code> call 
     * back is called.
     * 
     * @param address           the remote address to which to connect
     * @param port              the remote port
     * @param listener          the <code>IOHandler</code> that will
     *                          receive the associated connection events.
     */
    public IOHandle connect(InetAddress address, int port, IOHandler listener);
    
    /**
     * Attempts to connect to the remote host on the given port.  This call is
     * non-blocking, so the returned {@code IOHandle} should not be 
     * considered live until the {@code IOHandler.connected()} call 
     * back is called.  The given {@code IOFilter} will be attached to the
     * returned {@code IOHandle} and in use for the life of the handle.
     * 
     * @param address           the remote address to which to connect
     * @param port              the remote port
     * @param listener          the {@code IOHandler} that will
     *                          receive the associated connection events.
     * @param filter            the filter to attach to the returned 
     *                          {@code IOHandle}                          
     */
    public IOHandle connect(InetAddress address, int port, IOHandler listener, 
                            IOFilter filter);    
    
    
    /**
     * Shuts down this Connector.  Once shutdown, it cannot be restarted.
     *
     */
    public void shutdown();
}
