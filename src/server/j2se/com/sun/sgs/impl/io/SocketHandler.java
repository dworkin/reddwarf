package com.sun.sgs.impl.io;

import java.nio.ByteBuffer;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;

import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 *  This is an adapter between an Apache Mina {@link IoHandler} and the SGS
 *  IO framework {@link IOHandler}.  This is used on the client side to 
 *  pass messages from Mina onto the associated {@code IOHandler}.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SocketHandler implements IoHandler {
    
    private IOHandler handler;
    
    /**
     * Constructs a new {@code SocketHandler}.  As callbacks get fired from
     * the Apache Mina framework, they are forwarded on to the given
     * {@code IOHandler}.
     * 
     * @param handler           the {@code IOHandler} that will receive 
     *                          callbacks.
     */
    public SocketHandler(IOHandler handler) {
        this.handler = handler;
    }

    /**
     * Called by the Mina framework when the given session is closed.  Forward
     * this onto the associated handler as "disconnected".
     */
    public void sessionClosed(IoSession session) throws Exception {
        handler.disconnected(getHandle(session));
    }

    /**
     * This call-back is fired when there is an exception somewhere in Mina's
     * framework.  The exception is forwarded onto the associated 
     * {@code IOHandler}. 
     * 
     * @param session           the session where the exception occurred
     * @param throwable         the exception
     */
    public void exceptionCaught(IoSession session, Throwable exception)
                                                            throws Exception {
        
        handler.exceptionThrown(exception, getHandle(session));
        
    }

    public void messageReceived(IoSession session, Object message) throws Exception {
        org.apache.mina.common.ByteBuffer minaBuffer = 
                (org.apache.mina.common.ByteBuffer) message;
        
        
        java.nio.ByteBuffer nioBuffer = 
                        java.nio.ByteBuffer.allocate(minaBuffer.remaining());
        nioBuffer.put(minaBuffer.buf());
        nioBuffer.flip();
        handler.messageReceived(nioBuffer, getHandle(session));
        
    }

    private IOHandle getHandle(IoSession session) {
        return (IOHandle) session.getAttachment();
    }
    
    
    // these call-backs aren't used.
    public void sessionCreated(IoSession session) throws Exception {}

    public void sessionOpened(IoSession arg0) throws Exception {}
    
    public void sessionIdle(IoSession arg0, IdleStatus arg1) throws Exception {}
    
    public void messageSent(IoSession arg0, Object arg1) throws Exception {}

}
