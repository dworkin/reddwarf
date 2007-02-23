/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.io;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;

/**
 * This is a socket implementation of an {@link Connection} using the Apache
 * MINA framework.  It uses a {@link IoSession MINA IoSession} to handle the
 * IO transport.
 */
public class SocketConnection implements Connection, FilterListener {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketConnection.class.getName()));

    /** The {@link ConnectionListener} for this {@code Connection}. */
    private final ConnectionListener listener;

    /** The {@link CompleteMessageFilter} for this {@code Connection}. */
    private final CompleteMessageFilter filter;

    /** The {@link IoSession} for this {@code Connection}. */
    private final IoSession session;

    /**
     * Construct a new SocketConnection with the given listener, filter, and
     * session.
     * 
     * @param listener the {@code ConnectionListener} for the
     *        {@code Connection}
     * @param filter the {@code CompleteMessageFilter} for the
     *        {@code Connection}
     * @param session the {@code IoSession} for the {@code Connection}
     */
    SocketConnection(ConnectionListener listener, CompleteMessageFilter filter,
                 IoSession session)
    {
        if (listener == null || filter == null || session == null) {
            throw new NullPointerException("null argument to constructor");
        }
        this.listener = listener;
        this.filter = filter;
        this.session = session;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation prepends the length of the given byte array as
     * a 4-byte {@code int} in network byte-order, and sends it out on
     * the underlying MINA {@code IoSession}.
     * 
     * @param message the data to send
     * @throws IOException if the session is not connected
     */
    public void sendBytes(byte[] message) throws IOException {
        if (!session.isConnected()) {
            IOException ioe = new IOException(
                "SocketConnection.close: session not connected");
            logger.logThrow(Level.FINE, ioe, ioe.getMessage());
        }

        // The filter does the actual work to prepend the length
        filter.filterSend(this, message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation closes the underlying {@code IoSession}.
     *  
     * @throws IOException if the session is not connected
     */
    public void close() throws IOException {
        logger.log(Level.FINER, "session = {0}", session);
        if (!session.isConnected()) {
            IOException ioe = new IOException(
                "SocketConnection.close: session not connected");
            logger.logThrow(Level.FINE, ioe, ioe.getMessage());
        }
        session.close();
    }

    // Implement FilterListener

    /**
     * Dispatches a complete message to this connection's
     * {@code ConnectionListener}.
     * 
     * @param buf a {@code MINA ByteBuffer} containing the message to dispatch
     */
    public void filteredMessageReceived(ByteBuffer buf) {
        byte[] message = new byte[buf.remaining()];
        buf.get(message);
        listener.bytesReceived(this, message);
    }

    /**
     * Sends the given MINA buffer out on the associated {@code IoSession}.
     * 
     * @param buf the {@code MINA ByteBuffer} to send
     */
    public void sendUnfiltered(ByteBuffer buf) {
        logger.log(Level.FINEST, "message = {0}", buf);
        session.write(buf);
    }

    // specific to SocketConnection

    /**
     * Returns the {@code ConnectionListener} for this connection. 
     * 
     * @return the listener associated with this connection
     */
    ConnectionListener getConnectionListener() {
        return listener;
    }

    /**
     * Returns the {@code IOFilter} associated with this connection.
     * 
     * @return the associated filter
     */
    CompleteMessageFilter getFilter() {
        return filter;
    }
}
