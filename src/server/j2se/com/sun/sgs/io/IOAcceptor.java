package com.sun.sgs.io;

import java.io.IOException;

/**
 * {@code IOAcceptor}s bind to addresses and listen for incoming
 * connections. Clients should call {@link #shutdown} when finished with
 * them.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOAcceptor<T> {

    /**
     * Accepts incoming connections on the address it is configured with.
     * {@link IOAcceptorListener#newHandle} is called as new
     * {@link IOHandle}s connect. A new instance of the given filter class
     * will be attached to each incoming {@link IOHandle}.
     * 
     * @param listener the listener that will be notified of new connections
     * @param filterClass the type of filter to attach to incoming
     *        {@link IOHandle}s
     * 
     * @throws IOException if there was a problem binding to the port
     * @throws IllegalStateException if the IOAcceptor has been shutdown
     */
    void listen(IOAcceptorListener listener,
            Class<? extends IOFilter> filterClass) throws IOException;

    /**
     * Accepts incoming connections on the address it is configured with.
     * {@link IOAcceptorListener#newHandle} is called as new
     * {@link IOHandle}s connect.
     * 
     * @param listener the listener that will be notified of new connections
     * 
     * @throws IOException if there was a problem binding to the port
     * @throws IllegalStateException if the IOAcceptor has been shutdown
     */
    void listen(IOAcceptorListener listener) throws IOException;

    /**
     * Returns the {@link Endpoint} on which this IOAcceptor is listening.
     * 
     * @return the {@link Endpoint} on which this IOAcceptor is listening.
     */
    Endpoint<T> getEndpoint();

    /**
     * Shuts down this {@code IOAcceptor}. Shutdown is custom to each
     * implementation, but generally this consists of unbinding to any
     * listening ports. Once shutdown, it cannot be restarted.
     */
    void shutdown();
}
