package com.sun.sgs.impl.io;

import java.io.IOException;
import java.lang.annotation.Inherited;
import java.nio.ByteBuffer;

import org.apache.mina.common.IoFuture;
import org.apache.mina.common.IoFutureListener;
import org.apache.mina.common.IoSession;

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
public class SocketHandle implements IOHandle, IoFutureListener {

    private IOHandler handler;
    private IoSession session;
    
    /**
     * {@inheritDoc}
     * 
     * The incoming buffer should have its position set at the begining of the 
     * chunk of bytes to be written (that is, the buffer should be "flipped").
     * <p>
     * Note that a new ByteBuffer is created with each call to sendMessage so
     * that the client can continue to use the "message" buffer without fear of
     * tampering.  This is an inefficiency though, and perhaps should be 
     * replaced with a pool at some point.
     */
    public void sendMessage(ByteBuffer message) throws IOException {
        ByteBuffer nioBuffer = ByteBuffer.allocate(message.remaining());
        nioBuffer.put(message);
        nioBuffer.flip();
        
        org.apache.mina.common.ByteBuffer minaBuffer = 
            org.apache.mina.common.ByteBuffer.wrap(nioBuffer);
        session.write(minaBuffer);
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        session.close();
    }
    
    /**
     * {@inheritDoc}
     */
    public void setIOHandler(IOHandler handler) {
        this.handler = handler;
    }

    /**
     * IoFutureListener call-back.  Once this is called, the handle is 
     * considered "connected".  
     */
    public void operationComplete(IoFuture future) {
        setSession(future.getSession());
        handler.connected(this);
    }
    
    // specific to SocketHandle
    
    IOHandler getIOHandler() {
        return handler;
    }
    
    void setSession(IoSession session) {
        this.session = session;
        session.setAttachment(this);
    }
}
