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
     * Invoked by the associated {@link IOAcceptor} to obtain an appropriate
     * {@link IOHandler} for a newly-accepted connection.
     * The connection's {@link IOHandle} will be passed to the
     * {@code connected} method of the returned {@code IOHandler}
     * when the connection is fully established.
     *
     * @return an {@link IOHandler} that will receive events for the
     *          newly-accepted connection
     */
    IOHandler newHandle();

    /**
     * Invoked when the associated {@link IOAcceptor} is shut down.
     */
    void disconnected();

}
