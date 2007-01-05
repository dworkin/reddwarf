package com.sun.sgs.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collection;

/**
 * <code>IOAcceptor</code>s bind to addresses and listen for incoming connections.
 * Clients should call shutdown() when finished with them.
 * 
 * @author      Sten Anderson
 * @since       1.0
 */
public interface IOAcceptor {
    
    /**
     * Accepts incoming connections on the given address.  
     * {@code AcceptedHandleListener.newHandle} is called as new 
     * {@code IOHandle}s connect.  A new instance of the given filter class
     * will be attached to each incoming {@code IOHandle}.
     * 
     * @param address                   the address on which to listen
     * @param listener                  the listener that will be notified
     *                                  of new connections
     * @param filterClass               the type of filter to attach to incoming
     *                                  {@code IOHandle}s
     * 
     * @throws IOException if there was a problem binding to the port
     * @throws IllegalStateException if the IOAcceptor has been shutdown
     */
    public void listen(SocketAddress address, AcceptedHandleListener listener, 
                    Class<? extends IOFilter> filterClass) throws IOException;
    
    /**
     * Accepts incoming connections on the given address.  
     * {@code AcceptedHandleListener.newHandle} is called as new 
     * {@code IOHandle}s connect.
     * 
     * @param address                   the address on which to listen
     * @param listener                  the listener that will be notified
     *                                  of new connections
     * 
     * @throws IOException if there was a problem binding to the port
     * @throws IllegalStateException if the IOAcceptor has been shutdown
     */
    public void listen(SocketAddress address, AcceptedHandleListener listener)
                                                        throws IOException;
                                                       
    
    /**
     * Stops listening for incoming connections on the given address.  If the 
     * acceptor was not listening on the given address, this method simply returns.
     * 
     * @param address           the address on which to stop listening
     */
    public void unbind(SocketAddress address);
    
    /**
     * Returns a {@code Collection} of ports on which this acceptor is listening. 
     * 
     * @return a {@code Collection} of listening ports
     */
    public Collection<SocketAddress> listAddresses();
    
    /**
     * Shuts down this <code>IOAcceptor</code>.  Shutdown is custom to each
     * implementation, but generally this consists of unbinding to any listening
     * ports.  Once shutdown, it cannot be restarted.
     *
     */
    public void shutdown();
}
