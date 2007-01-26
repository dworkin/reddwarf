package com.sun.sgs.io;

/**
 * Receives asynchronous notification of events from an associated
 * {@link IOAcceptor}.  The {@code newHandle()} method is invoked when
 * a connection has been accepted to obtain an appropriate
 * {@link IOHandler} from the listener.  When the
 * {@code IOAcceptor} is shut down, the listener is notified by
 * invoking its {@code disconnected()} method.
 */
public interface IOAcceptorListener {

    /**
     * Returns an appropriate {@link IOHandler} for a newly-accepted
     * connection.  The {@link IOHandle} for the new connection is
     * passed to the {@link IOHandler#connected connected} method of the
     * returned {@code IOHandler} once the connection is fully established.
     *
     * @return an {@link IOHandler} to receive events for the
     *          newly-accepted connection
     */
    IOHandler newHandle();

    /**
     * Notifies this listener that its associated {@link IOAcceptor}
     * has shut down.
     */
    void disconnected();

}
