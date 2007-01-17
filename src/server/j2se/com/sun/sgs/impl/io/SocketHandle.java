package com.sun.sgs.impl.io;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * This is a socket implementation of an {@code IOHandle} using the Apache
 * Mina framework.  This implementation uses an {@link IoSession} to handle the
 * IO transport.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public class SocketHandle implements IOHandle {
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketHandle.class.getName()));

    /** The {@link IOHandler} for this {@code IOHandle}. */
    private final IOHandler handler;

    /** The {@link IOFilter} for this {@code IOHandle}. */
    private final IOFilter filter;

    /** The {@link IoSession} for this {@code IOHandle}. */
    private final IoSession session;

    /**
     * Construct a new SocketHandle with the given handle, filter,
     * and session.
     * 
     * @param handler the {@link IOHandler} for the {@code IOHandle}
     * @param filter the {@link IOFilter} for the {@code IOHandle}
     * @param session the {@link IoSession} for the {@code IOHandle}
     */
    SocketHandle(IOHandler handler, IOFilter filter, IoSession session) {
        this.handler = handler;
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
     * @throws IOException if the session is not connected.
     */
    public void sendBytes(byte[] message) throws IOException {
        logger.log(Level.FINEST, "message = {0}", message);
        if (!session.isConnected()) {
            throw new IOException("session not connected");
        }
        ByteBuffer buffer = ByteBuffer.allocate(message.length + 4);
        buffer.putInt(message.length);
        buffer.put(message);
        buffer.flip();
        byte[] messageWithLength = new byte[buffer.remaining()];
        buffer.get(messageWithLength);
        
        filter.filterSend(this, messageWithLength);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation closes the underlying {@code IoSession}.
     *  
     * @throws IOException if the session is not connected. 
     */
    public void close() throws IOException {
        logger.log(Level.FINE, "session = {0}", session);
        if (!session.isConnected()) {
            throw new IOException("SocketHandle.close: session not connected");
        }
        session.close();
    }
    
    // specific to SocketHandle
    
    /**
     * Returns the {@code IOHandler} for this handle. 
     * 
     * @return the handler associated with this handle
     */
    IOHandler getIOHandler() {
        return handler;
    }
    
    /**
     * Returns the {@code IOFilter} associated with this handle.
     * 
     * @return the associated filter
     */
    IOFilter getFilter() {
        return filter;
    }
    
    /**
     * Sends this message wrapped in a MINA buffer.
     * 
     * @param message the byte message to send
     */
    void doSend(byte[] message) {
        ByteBuffer minaBuffer = ByteBuffer.allocate(message.length);
        minaBuffer.put(message);
        minaBuffer.flip();
        
        doSend(minaBuffer);
    }
    
    /**
     * Sends the given MINA buffer out on the associated {@code IoSession}.
     * 
     * @param messageBuffer the {@code MINA ByteBuffer} to send
     */
    private void doSend(ByteBuffer messageBuffer) {
        logger.log(Level.FINEST, "message = {0}", messageBuffer);
        
        session.write(messageBuffer);
    }
}
