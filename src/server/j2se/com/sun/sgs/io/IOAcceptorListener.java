package com.sun.sgs.io;

/**
 * Listens for events on an associated {@link IOAcceptor}.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOAcceptorListener {

    /**
     * Requests an {@link IOHandler} that will be associated with a newly
     * accepted connection. The new connection's {@link IOHandle} will be
     * passed to {@link IOHandler#connected} when the connection has been
     * established.
     * 
     * @return an {@link IOHandler} that will receive events for the new
     *         {@link IOHandle}
     */
    IOHandler newHandle();

    /**
     * Called when the associated {@link IOAcceptor} is closed.
     */
    void disconnected();
}