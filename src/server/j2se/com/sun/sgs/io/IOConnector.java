package com.sun.sgs.io;

import java.net.InetAddress;

/**
 * An <code>IOConnector</code> establishes connections, or 
 * <code>IOHandles</code>, to remote hosts.  Clients should call shutdown()
 * when finished with the Connector.
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
     * Shuts down this Connector.  Once shutdown, it cannot be restarted.
     *
     */
    public void shutdown();
}
