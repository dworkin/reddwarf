/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.io;

/**
 * Receives asynchronous notification of events from an associated
 * {@link Acceptor}.  The {@link #newConnection newConnection} method
 * is invoked when a connection has been accepted to obtain an appropriate
 * {@link ConnectionListener} from this  listener.  When the
 * {@code Acceptor} is shut down, the listener is notified by
 * invoking its {@code disconnected()} method.
 */
public interface AcceptorListener {

    /**
     * Returns an appropriate {@link ConnectionListener} for a newly-accepted
     * connection.  The new {@link Connection} is  passed to the
     * {@link ConnectionListener#connected connected} method of the
     * returned {@code ConnectionListener} once it is fully established.
     *
     * @return a {@code ConnectionListener} to receive events for the
     *          newly-accepted {@code Connection}
     */
    ConnectionListener newConnection();

    /**
     * Notifies this listener that its associated {@link Acceptor}
     * has shut down.
     */
    void disconnected();

}
