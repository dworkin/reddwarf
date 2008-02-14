/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
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

package com.sun.sgs.impl.io;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.Connector;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;

/**
 * An adapter between an Apache {@link IoHandler MINA IoHandler} and the SGS
 * IO framework {@link ConnectionListener}. SocketHandlers exist one per
 * {@link Connection} on the client {@link Connector} side, and exist one per
 * {@link Acceptor} on the server side.
 */
class SocketConnectionListener extends IoHandlerAdapter {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(
                SocketConnectionListener.class.getName()));

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to {@link ConnectionListener#connected}.
     */
    @Override
    public void sessionOpened(IoSession session) throws Exception
    {
        SocketConnection conn = (SocketConnection) session.getAttachment();
        logger.log(Level.FINE, "opened session {0}", session);
        ConnectionListener listener = conn.getConnectionListener();
        listener.connected(conn);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to {@link ConnectionListener#disconnected}.
     */
    @Override
    public void sessionClosed(IoSession session) throws Exception
    {
        SocketConnection conn = (SocketConnection) session.getAttachment();
        logger.log(Level.FINE, "disconnect on {0}", conn);
        ConnectionListener listener = conn.getConnectionListener();
        listener.disconnected(conn);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to {@link ConnectionListener#exceptionThrown}.
     */
    @Override
    public void exceptionCaught(IoSession session, Throwable exception)
        throws Exception
    {
        SocketConnection conn = (SocketConnection) session.getAttachment();
        logger.logThrow(Level.FINER, exception, "exception on {0}", conn);
        if (conn == null) {
            return;
        }

        ConnectionListener listener = conn.getConnectionListener();
        listener.exceptionThrown(conn, exception);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Obtains the {@link CompleteMessageFilter} for the associated
     * {@link Connection}, and forwards incoming data to the filter's
     * {@link CompleteMessageFilter#filterReceive filterReceive} method.
     */
    @Override
    public void messageReceived(IoSession session, Object message)
        throws Exception
    {
        SocketConnection conn = (SocketConnection) session.getAttachment();

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "recv on {0}: {1}", conn, message);
        }
        
        ByteBuffer buf = (ByteBuffer) message;
        try {
            conn.getFilter().filterReceive(conn, buf);
        } catch (RuntimeException e) {
            logger.logThrow(Level.FINER, e,
                "exception in recv of {0}:", buf);
            throw e;
        }
    }
}
