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
    
    public SocketHandle() {
    }
    
    /**
     * {@inheritDoc}
     * 
     * The incoming buffer should be at "position = limit", that is not flipped.
     */
    public void sendMessage(ByteBuffer message) throws IOException {
        message.flip();
        org.apache.mina.common.ByteBuffer minaBuffer = 
            org.apache.mina.common.ByteBuffer.wrap(message);
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

    // IoFutureListener method
    
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
