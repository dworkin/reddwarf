package com.sun.sgs.impl.io;

import java.io.IOException;
import java.lang.annotation.Inherited;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFuture;
import org.apache.mina.common.IoFutureListener;
import org.apache.mina.common.IoSession;

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
public class SocketHandle implements IOHandle, IoFutureListener {

    private IOHandler handler;
    private IoSession session;
    private IOFilter filter;
    
    SocketHandle(IOFilter filter) {
        this.filter = filter;
    }
    
    /**
     * Sends the given byte message out on the underlying {@code IoSession}.
     * 
     * @throws IOException if the session is not connected.
     */
    public void sendBytes(byte[] message) throws IOException {
        if (!session.isConnected()) {
            throw new IOException("SocketHandle.sendMessage: session not connected");
        }
        filter.filterSend(this, message);
    }

    /**
     * Closes the undering {@code IoSession}.
     *  
     * @throws IOException if the session is not connected. 
     */
    public void close() throws IOException {
        if (!session.isConnected()) {
            throw new IOException("SocketHandle.close: session not connected");
        }
        session.close();
    }
    
    /**
     * IoFutureListener call-back.  Once this is called, the handle is 
     * considered "connected".  
     */
    public void operationComplete(IoFuture future) {
        if (session != null) {
            return;
        }
        setSession(future.getSession());
        handler.connected(this);
    }
    
    // specific to SocketHandle
    
    /**
     * Sets the {@code IOHandler} for this handle.
     * 
     * @param handler           the handler to receive callbacks for this handle
     */
    void setIOHandler(IOHandler handler) {
        this.handler = handler;
    }
    
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
     * Sends this message wrapped in a Mina buffer.
     * 
     * @param message           the byte message to send 
     */
    void doSend(byte[] message) {
        ByteBuffer minaBuffer = ByteBuffer.allocate(message.length);
        minaBuffer.put(message);
        minaBuffer.flip();
        
        doSend(minaBuffer);
    }
    
    /**
     * Sends the given Mina buffer out on the associated {@code IoSession}.
     * 
     * @param messageBuffer
     */
    void doSend(ByteBuffer messageBuffer) {
        session.write(messageBuffer);
    }
    
    /**
     * Sets the {@code IoSession} associated with this handle.  Sessions and
     * handles have a one-to-one mapping.
     * 
     * @param session
     */
    void setSession(IoSession session) {
        this.session = session;
        session.setAttachment(this);
    }
}
