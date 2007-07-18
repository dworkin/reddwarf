/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
