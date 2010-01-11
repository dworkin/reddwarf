/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
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
